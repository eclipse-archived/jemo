/*
 ********************************************************************************
 * Copyright (c) 9th November 2018 Cloudreach Limited Europe
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the Eclipse
 * Public License, v. 2.0 are satisfied: GNU General Public License, version 2
 * with the GNU Classpath Exception which is
 * available at https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 ********************************************************************************/
package org.eclipse.jemo.sys;

import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.api.*;
import org.eclipse.jemo.api.Module;
import org.eclipse.jemo.inject.JemoProvider;
import org.eclipse.jemo.internal.model.*;
import org.eclipse.jemo.internal.model.JemoError;
import org.eclipse.jemo.sys.auth.JemoAuthentication;
import org.eclipse.jemo.sys.auth.JemoGroup;
import org.eclipse.jemo.sys.auth.JemoUser;
import org.eclipse.jemo.sys.internal.ManagedAcceptor;
import org.eclipse.jemo.sys.internal.ManagedConsumer;
import org.eclipse.jemo.sys.internal.ManagedFunction;
import org.eclipse.jemo.sys.internal.ManagedFunctionWithException;
import org.eclipse.jemo.sys.internal.SystemDB;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.jemo.sys.microprofile.JemoConfig;
import org.eclipse.jemo.sys.microprofile.JemoConfigProviderResolver;
import org.eclipse.jemo.sys.microprofile.JemoConfigSource;
import org.eclipse.jemo.sys.microprofile.MicroProfileConfigSource;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;
import org.eclipse.microprofile.config.spi.ConfigSource;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.enterprise.inject.spi.DeploymentException;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.xml.ws.Holder;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

import static java.util.Arrays.asList;
import static org.eclipse.jemo.sys.JemoRuntimeAdmin.JEMO_ADMIN;
import static org.eclipse.jemo.sys.JemoRuntimeAdmin.JEMO_PLUGINS;
import static org.eclipse.jemo.sys.JemoRuntimeSetup.JEMO_SETUP;

/**
 * the plugin manager will give a new instance of a plugin based on his id's
 * <p>
 * essentially the plugin manager is one of the core components of the system
 * and it builds the basis for the micro-kernel architecture of Cloudreach Connect.
 * <p>
 * The class loader hiearchy should be as follows:
 * <p>
 * - 1. create an isolated class loader which stems from a system class class loader binded to String.class which
 * then contains all of the dependant libraries within the plugin context, we will also inject the latest stable
 * cloudreach connect implementation for the library version.
 * <p>
 * Jemo will store libraries on AWS S3 and should allow them to be downloaded through maven directly through
 * a cloudreach connect endpoint.
 * 
 * Update: as we move to support some Microprofile the plugin manager will manager global micro-profile services like configuration
 * and inject them into our plugins so they can access the supporting Jemo services.
 *
 * @author Christopher Stura "cstura@gmail.com"
 */
public class JemoPluginManager {

    private static final String QUEUE_NAME_PREFIX = "JEMO-";
    private static final String DEFAULT_PLUGIN_JAR_FILE_NAME = "0_DefaultPlugin-1.0.jar";
    private static final String STATS_PLUGIN_JAR_FILE_NAME = "1_StatsPlugin-1.0.jar";

    public static class MonitoringInterval {
        private String key;
        private long duration;
        private long httpRequests = 0;
        private long eventRequests = 0;
        private long totalHttpTime = 0;
        private long totalEventTime = 0;
        private long intervalStart = System.currentTimeMillis();

        public MonitoringInterval(String key, long duration, TimeUnit unit) {
            this.key = key;
            this.duration = TimeUnit.MILLISECONDS.convert(duration, unit);
        }

        @JsonIgnore
        public void httpRequest(long durationInMilliseconds) {
            checkInterval();
            this.httpRequests++;
            this.totalHttpTime += durationInMilliseconds;
        }

        @JsonIgnore
        public void eventRequest(long durationInMilliseconds) {
            checkInterval();
            this.eventRequests++;
            this.totalEventTime += durationInMilliseconds;
        }

        @JsonIgnore
        private void checkInterval() {
            if (System.currentTimeMillis() - intervalStart > duration) {
                this.intervalStart = System.currentTimeMillis();
                this.httpRequests = 0;
                this.eventRequests = 0;
                this.totalEventTime = 0;
                this.totalHttpTime = 0;
            }
        }

        public String getKey() {
            return key;
        }

        public long getDuration() {
            return duration;
        }

        public long getHttpRequests() {
            return httpRequests;
        }

        public long getEventRequests() {
            return eventRequests;
        }

        public long getTotalHttpTime() {
            return totalHttpTime;
        }

        public long getTotalEventTime() {
            return totalEventTime;
        }
    }

    public static class ModuleInfoCache {
        private String location;
        private long cachedOn = System.currentTimeMillis();
        private Set<ModuleInfo> activeModules;

        public ModuleInfoCache(String location, Set<ModuleInfo> activeModules) {
            this.location = location;
            this.activeModules = activeModules;
        }

        public String getLocation() {
            return location;
        }

        public long getCachedOn() {
            return cachedOn;
        }

        public Set<ModuleInfo> getActiveModules() {
            return activeModules;
        }

        public boolean isExpired() {
            return cachedOn + TimeUnit.MINUTES.toMillis(5) < System.currentTimeMillis();
        }
    }

    public static String VHOST_KEY = "VIRTUALHOSTS";

    public static final String EVENT_MODULE_UPLOAD = "MODULE_UPLOAD";
    
    static {
    	//we should register the Jemo implementation of the Micro-profile ConfigProviderResolver
    	ConfigProviderResolver.setInstance(new JemoConfigProviderResolver());
    }

    private final Map<String, Set<JemoModule>> LIVE_MODULE_MAP = new ConcurrentHashMap<>();
    private final Map<String, String> moduleEndpointMap = new ConcurrentHashMap<>();
    private final Map<String, List<ModuleEventListener>> eventListeners = new ConcurrentHashMap<>();
    private final Map<String, String> virtualHostMap = new ConcurrentSkipListMap<>((o1, o2) -> Integer.valueOf(o1.length()).compareTo(o2.length()) == 0 ? o1.compareTo(o2) : Integer.valueOf(o1.length()).compareTo(o2.length())); //a virtual host definition will be mapped to an actual module endpoint.
    private int TIMEOUT_COUNT = 0;
    private int MAX_TIMEOUT_COUNT = 10;
    private static long MEMORY_THRESHOLD = 100000;
    static final String MODULE_METADATA_TABLE = "eclipse_jemo_modules";
    private final Map<String, JemoApplicationMetaData> KNOWN_APPLICATIONS = new ConcurrentHashMap<>();
    
    /**
     * this application list will contain the list of applications which are valid to be executed
     * within this application container.
     */
    private final List<JemoApplicationMetaData> APPLICATION_LIST = new CopyOnWriteArrayList<>();
    private final Map<String, ModuleInfoCache> LIVE_MODULE_CACHE = new ConcurrentHashMap<>();

    private final MonitoringInterval[] SYSTEM_INTERVALS = new MonitoringInterval[]{
            new MonitoringInterval("5M", 5, TimeUnit.MINUTES),
            new MonitoringInterval("15M", 15, TimeUnit.MINUTES),
            new MonitoringInterval("30M", 30, TimeUnit.MINUTES),
            new MonitoringInterval("60M", 60, TimeUnit.MINUTES),
            new MonitoringInterval("1D", 1, TimeUnit.DAYS)
    };

    private final ExecutorService MODULE_LOADER = Executors.newFixedThreadPool(25); //load 50 modules at the same time.
    private final AbstractJemo jemoServer;
    private boolean isStartup = true;
    private PluginManagerModule PLUGIN_MANAGER_MODULE;
    private DeploymentHistoryModule DEPLOYMENT_HISTORY_MODULE;
    private ModulesStatsModule MODULES_STATS_MODULE;
    

    public JemoPluginManager(final AbstractJemo jemoServer) {
        this.jemoServer = jemoServer;

        if (!jemoServer.isInInstallationMode()) {
            //make sure the plugin buckets exist and if not create them.
            //when we start we will load the virtual host definitions into memory.
            loadVirtualHostDefinitions();

            //since each of the items in this bucket will be a jar file, we need to load all of the bytes into a memory area, we will then load these classes
            //seperately based on the plugin needs and configuration. (we will expect the files to follow a naming convention like the following)
            //library name-version.jar if no version is specified then the library will be defaulted to 1.0
            //we want to avoid scanning the S3 bucket for modules and instead we should be picking items up from a dynamo db table instead.

            listApplications().forEach(app -> setApplicationMetaData(app));
            jemoServer.LOG(Level.INFO, "[%s] Discovered %d modules %s", getClass().getSimpleName(), getApplicationList().size(),
            		getApplicationList().stream()
                            .map(app -> app.getId()).collect(Collectors.joining(","))
            );
        }

        PLUGIN_MANAGER_MODULE = new PluginManagerModule(jemoServer);
        DEPLOYMENT_HISTORY_MODULE = new DeploymentHistoryModule();
        MODULES_STATS_MODULE = new ModulesStatsModule(KNOWN_APPLICATIONS);
        addDefaultModulesToModuleMap();

        //load on startup is not a good idea instead we are going to lazy load things when they are requested so we can have smaller clusters running more stuff.
        //so in the beginning all we really need is to know what the potential endpoints for the valid modules are.
        getApplicationList().parallelStream()
                .forEach(app -> {
                    moduleEndpointMap.putAll(app.getEndpoints().values().stream()
                            .collect(Collectors.toMap(e -> e, e -> app.getId())));
                });

        addDefaultModuleToAppList("PluginManager", asList(PLUGIN_MANAGER_MODULE), DEFAULT_PLUGIN_JAR_FILE_NAME);
        addDefaultModuleToAppList("StatsPlugin", asList(DEPLOYMENT_HISTORY_MODULE, MODULES_STATS_MODULE), STATS_PLUGIN_JAR_FILE_NAME);

        if (!jemoServer.isInInstallationMode()) {
            storeModuleList();
        }
        isStartup = false;
    }
    
    private synchronized void setApplicationMetaData(JemoApplicationMetaData appMetadata) {
    	APPLICATION_LIST.removeIf(md -> md.getId().equalsIgnoreCase(appMetadata.getId()));
    	APPLICATION_LIST.add(appMetadata);
    	final Set<String> deadEndpointMappings = moduleEndpointMap.entrySet().stream().filter(e -> appMetadata.getId().equals(e.getValue())).map(e -> e.getKey()).collect(Collectors.toSet());
        deadEndpointMappings.forEach(k -> moduleEndpointMap.remove(k));
        moduleEndpointMap.putAll(appMetadata.getEndpoints().values().stream()
                .collect(Collectors.toMap(e -> e, e -> appMetadata.getId())));
	    if(appMetadata.getId().equals(DEFAULT_PLUGIN_JAR_FILE_NAME)) {
	    	moduleEndpointMap.put(PLUGIN_MANAGER_MODULE.getBasePath(), DEFAULT_PLUGIN_JAR_FILE_NAME);
    	}
    }
    
    private synchronized void removeApplication(final String applicationId) {
    	APPLICATION_LIST.removeIf(md -> md.getId().equalsIgnoreCase(applicationId));
    	final Set<String> deadEndpoints = moduleEndpointMap.entrySet().stream().filter(e -> e.getValue().equalsIgnoreCase(applicationId)).map(e -> e.getKey()).collect(Collectors.toSet());
    	deadEndpoints.forEach(ep -> moduleEndpointMap.remove(ep));
    	//we need to update the know applications
    	listApplications();
    }

    private void addDefaultModulesToModuleMap() {
        final JemoModule pluginManager = createAndStartDefaultModule(PLUGIN_MANAGER_MODULE, DEFAULT_PLUGIN_JAR_FILE_NAME);
        LIVE_MODULE_MAP.put(DEFAULT_PLUGIN_JAR_FILE_NAME, new HashSet<JemoModule>() {{
            add(pluginManager);
        }});

        final JemoModule deploymentHistory = createAndStartDefaultModule(DEPLOYMENT_HISTORY_MODULE, STATS_PLUGIN_JAR_FILE_NAME);
        final JemoModule moduleStats = createAndStartDefaultModule(MODULES_STATS_MODULE, STATS_PLUGIN_JAR_FILE_NAME);
        LIVE_MODULE_MAP.put(STATS_PLUGIN_JAR_FILE_NAME, new HashSet<JemoModule>() {{
            add(deploymentHistory);
            add(moduleStats);
        }});
    }

    private JemoModule createAndStartDefaultModule(Module module, String pluginJarName) {
        final int pluginId = PLUGIN_ID(pluginJarName);
        final double pluginVersion = PLUGIN_VERSION(pluginJarName);

        final Logger moduleLogger = getModuleLogger(pluginId, pluginVersion, module.getClass());
        final JemoModule jemoModule = new JemoModule(module, new ModuleMetaData(pluginId, pluginVersion, module.getClass().getSimpleName(), pluginJarName, moduleLogger), null);
        module.construct(jemoModule.getMetaData().getLog(), jemoModule.getMetaData().getName(), jemoModule.getMetaData().getId(), jemoModule.getMetaData().getVersion());
        module.start();
        return jemoModule;
    }

    private void addDefaultModuleToAppList(String name, List<Module> modules, String pluginJarName) {
        final JemoApplicationMetaData pluginManagerApp = new JemoApplicationMetaData();

        pluginManagerApp.setId(pluginJarName);
        pluginManagerApp.setEnabled(true);
        final double pluginVersion = PLUGIN_VERSION(pluginJarName);
        pluginManagerApp.setVersion(pluginVersion);
        pluginManagerApp.setName(name);

        modules.forEach(module -> {
            addModuleToApplicationMetadata(pluginManagerApp, module);
            pluginManagerApp.getLimits().put(module.getClass().getName(), JemoApplicationMetaData.JemoModuleLimits.wrap(module.getLimits()));
        });
        
        setApplicationMetaData(pluginManagerApp);
    }

    /**
     * this method will lazy load the modules inside a specific application id
     *
     * @param jarFileName the name of the jar file which contains the module to load.
     * @return a list of the JemoModules associated with this application
     */
    public synchronized Set<JemoModule> loadModules(String jarFileName) throws IOException {
        if (!LIVE_MODULE_MAP.containsKey(jarFileName)) {
            jemoServer.LOG(Level.INFO, "[%s][%s] - I have received a request to load the module", getClass().getSimpleName(), jarFileName);
            loadPlugin(jarFileName); //ask to load the module
        }
        getApplicationList().stream()
                .filter(app -> app.getId().equals(jarFileName))
                .findFirst().ifPresent(app -> app.setLastUsedOn(System.currentTimeMillis()));

        return LIVE_MODULE_MAP.get(jarFileName);
    }

    public List<JemoApplicationMetaData> getApplicationList() {
        return APPLICATION_LIST;
    }

    public synchronized Set<JemoModule> getLoadedModules() {
        return LIVE_MODULE_MAP.values().stream()
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
    }

    /**
     * this method will return a list of all the deployed code in the GSM which this instance is running on which
     * contains an active code pattern.
     *
     * @return a unique list of the applications which are running on this particular GSM instance.
     */
    protected List<JemoApplicationMetaData> listApplications() {
        //step 1: we should see if we have a metadata table already created.
        SystemDB.createTable(MODULE_METADATA_TABLE);

        final Map<Boolean, List<JemoApplicationMetaData>> appMetaDataPartition = CloudProvider.getInstance().getRuntime().listNoSQL(MODULE_METADATA_TABLE, JemoApplicationMetaData.class).stream()
                .collect(Collectors.partitioningBy(JemoApplicationMetaData::isEnabled));

        //step 2: we need to check how much data is in here.
        appMetaDataPartition.get(true).forEach(jemoAppMetaData -> KNOWN_APPLICATIONS.put(jemoAppMetaData.getId(), jemoAppMetaData));

        final Set<String> disabledAppMetaDataIds = appMetaDataPartition.get(false).stream()
                .map(JemoApplicationMetaData::getId)
                .collect(Collectors.toSet());

        if (KNOWN_APPLICATIONS.isEmpty()) {
            //step 3: if we effectively don't have a list of the know applications then we should build one from scratch
            Set<String> fullAppEnabledList = CloudProvider.getInstance().getRuntime().listPlugins().stream()
                    .filter(appId -> !disabledAppMetaDataIds.contains(appId))
                    .collect(Collectors.toSet());

            if (!fullAppEnabledList.isEmpty()) {
            	 SystemDB.save(MODULE_METADATA_TABLE, fullAppEnabledList.stream()
                         .map(app -> {
                             JemoApplicationMetaData jemoApp = new JemoApplicationMetaData();
                             jemoApp.setId(app);
                             jemoApp.setEnabled(true);
                             jemoApp.setLastUpgradeDate(System.currentTimeMillis()); //the first entry lets use today
                             jemoApp.setLastUsedOn(System.currentTimeMillis());
                             jemoApp.setInstallDate(PLUGIN_INSTALLED_ON(app));
                             jemoApp.setName(PLUGIN_NAME(app));
                             jemoApp.setVersion(PLUGIN_VERSION(app));
                             return jemoApp;
                         }).toArray(JemoApplicationMetaData[]::new)); //this adds newly discovered applications uploaded via non 2.3 versions of Jemo for backwards compatibility.
            }
            jemoServer.LOG(Level.INFO, "[%s] Application List was created successfully", JemoPluginManager.class.getSimpleName());
            CloudProvider.getInstance().getRuntime().listNoSQL(MODULE_METADATA_TABLE, JemoApplicationMetaData.class)
            .forEach(jemoAppMetaData -> KNOWN_APPLICATIONS.put(jemoAppMetaData.getId(), jemoAppMetaData));
        }
        
        appMetaDataPartition.get(false).forEach(jemoAppMetaData -> KNOWN_APPLICATIONS.put(jemoAppMetaData.getId(), jemoAppMetaData));

        return new ArrayList<>(KNOWN_APPLICATIONS.values()); //we should return all applications because none will be loaded on startup.
    }

    public void loadVirtualHostDefinitions() {
        Map<String, String> newVhostMap = JemoVirtualHostManager.getVirtualHostDefinitions();
        if (newVhostMap != null) {
            synchronized (virtualHostMap) {
                virtualHostMap.clear();
                virtualHostMap.putAll(newVhostMap);
            }
        }
    }

    public Map<String, String> getVirtualHostMap() {
        return this.virtualHostMap;
    }

    public MonitoringInterval getMonitoringInterval(String intervalKey) {
        return Arrays.asList(SYSTEM_INTERVALS).parallelStream().filter(interval -> interval.getKey().equals(intervalKey)).findAny().orElse(null);
    }

    public Set<String> listMonitoringIntervals() {
        return Arrays.asList(SYSTEM_INTERVALS).parallelStream().map(MonitoringInterval::getKey).collect(Collectors.toSet());
    }

    protected boolean PLUGIN_VALID(String jarFileName) {
        return PLUGIN_VALID(PLUGIN_ID(jarFileName));
    }

    protected boolean PLUGIN_VALID(int pluginId) {
        return jemoServer.PLUGIN_VALID(pluginId);
    }

    public static int PLUGIN_ID(String jarFileName) {
        return Integer.parseInt(jarFileName.substring(0, jarFileName.indexOf('_')));
    }

    public static double PLUGIN_VERSION(String jarFileName) {
        return Double.parseDouble(jarFileName.substring(jarFileName.lastIndexOf('-') + 1, jarFileName.lastIndexOf(".jar")));
    }

    public static String PLUGIN_NAME(String jarFileName) {
        return jarFileName.substring(jarFileName.indexOf('_') + 1, jarFileName.lastIndexOf('-'));
    }

    protected static long PLUGIN_INSTALLED_ON(String jarFileName) {
        try {
            return CloudProvider.getInstance().getRuntime().getModuleInstallDate(jarFileName);
        } catch (Exception e) {
            return System.currentTimeMillis();
        }
    }

    protected static long MEMORY_CHECK() {
        long freeMemory = Runtime.getRuntime().freeMemory();
        garbageCollectIfNecessary(freeMemory);

        return freeMemory;
    }

    protected static boolean garbageCollectIfNecessary(long freeMemory) {
        if (freeMemory < 100000) {
            Runtime.getRuntime().gc();
            return true;
        }

        return false;
    }

    /**
     * this method will extract the list of Jemo modules given a class loader instance and the
     * list of classes which are within that class loaders scope.
     *
     * @param classList         the list of classes in the class loader scope
     * @param moduleClassLoader a reference to the class loader responsible for loading the classes in scope
     * @return a list of the Jemo modules managed by the class loader referenced.
     */
    protected List<String> MODULE_LIST(Collection<String> classList, JemoClassLoader moduleClassLoader) {
        return classList.parallelStream().filter(cls -> {
            try {
                Class classRef = moduleClassLoader.loadClass(cls);
                if (Module.class.isAssignableFrom(classRef) && !Modifier.isAbstract(classRef.getModifiers())) {
                	//we need to try and instantiate the module as well before we decide if it's valid.
                	classRef.getConstructor().newInstance();
                    return true;
                }
            } catch (ClassNotFoundException | NoClassDefFoundError | VerifyError | IllegalAccessError 
            		 | InvocationTargetException | IllegalAccessException | InstantiationException | NoSuchMethodException 
            		 | ExceptionInInitializerError clsNfEx) {
            }
            return false;
        }).collect(Collectors.toList());
    }

    protected List<String> MODULE_LIST(String jarFileName, byte[] jarBytes, JemoClassLoader moduleClassLoader) {
        //first lets try and pull the list of modules from our persistance layer.
        ArrayList<String> moduleList = new ArrayList<>();
        try {
            List<String> cachedModuleList = CloudProvider.getInstance().getRuntime().getModuleList(jarFileName);
            if (cachedModuleList == null && jarBytes != null) {
                //no cache we need to calculate it and if we do we may as well save it back to the cache.
                Set<String> classList = getClassList(jarBytes);
                cachedModuleList = MODULE_LIST(classList, moduleClassLoader);
                CloudProvider.getInstance().getRuntime().storeModuleList(jarFileName, cachedModuleList);
            }
            if(cachedModuleList != null) {
            	moduleList.addAll(cachedModuleList);
            }
        } catch (Throwable ex) {
            jemoServer.LOG(Level.WARNING, "[%s][%s] I was unable to load the list of modules because of the error: %s",
                    new Object[]{JemoPluginManager.class.getSimpleName(), jarFileName, JemoError.toString(ex)});
        }

        return moduleList;
    }

    public static File cacheStreamToFile(InputStream in) throws IOException {
        File tmpFile = File.createTempFile(UUID.randomUUID().toString(), ".stream_cache");
        Jemo.stream(new FileOutputStream(tmpFile), in, true);

        return tmpFile;
    }

    protected List<String> MODULE_LIST(String jarFileName) {
        ArrayList<String> moduleList = new ArrayList<>();
        try {
            List<String> cachedModuleList = CloudProvider.getInstance().getRuntime().getModuleList(jarFileName);
            if (cachedModuleList == null) {
                try (JemoClassLoader jemoClassLoader = buildPluginClassLoader(jarFileName)) {
                	if(jemoClassLoader != null) {
                		cachedModuleList = MODULE_LIST(jemoClassLoader.getClassList(), jemoClassLoader);
                	}
                }
                if(cachedModuleList != null) {
                	CloudProvider.getInstance().getRuntime().storeModuleList(jarFileName, cachedModuleList);
                }
            }
            if(cachedModuleList != null) {
            	moduleList.addAll(cachedModuleList);
            }
        } catch (Throwable ex) {
            jemoServer.LOG(Level.WARNING, "[%s][%s] I was unable to load the list of modules because of the error: %s",
                    new Object[]{JemoPluginManager.class.getSimpleName(), jarFileName, JemoError.toString(ex)});
        }

        return moduleList;
    }

    public Logger getModuleLogger(int moduleId, double moduleVersion, Class<? extends Module> mod) {
        Logger logger = Logger.getLogger(moduleId + ":" + moduleVersion + ":" + mod.getSimpleName());
        logger.setParent(jemoServer.getSYS_LOGGER());
        return logger;
    }

    public Set<JemoModule> loadPluginModules(int pluginId) {
        return getApplicationList().stream()
                .filter(app -> PLUGIN_ID(app.getId()) == pluginId)
                .map(JemoApplicationMetaData::getId)
                .flatMap(app -> Util.F(null, x -> loadModules(app).stream()))
                .collect(Collectors.toSet());
    }

    public Set<JemoModule> listPluginModules(int pluginId, double pluginVersion) {
        return LIVE_MODULE_MAP.entrySet().stream()
                .filter(entry -> PLUGIN_ID(entry.getKey()) == pluginId && PLUGIN_VERSION(entry.getKey()) == pluginVersion)
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(new HashSet<>());
    }

    public Set<Integer> listPluginIds() {
        return getApplicationList().stream().map(app -> PLUGIN_ID(app.getId())).collect(Collectors.toSet());
    }

    public Set<JemoApplicationMetaData> listPlugins() {
        return getApplicationList().stream().collect(Collectors.toSet());
    }

    public JemoModule loadModuleByClassName(int moduleId, String moduleClassName) {
        return loadPluginModules(moduleId).stream()
                .filter(m -> m.getModule().getClass().getName().equals(moduleClassName))
                .findAny().orElse(null);
    }

    public JemoMessage process(JemoMessage msg) throws Throwable {
        //so first of all this is actually used for both batch processing and standard processing.
        boolean isBatch = msg.getAttributes().containsKey("module_class");

        //get the set of valid applications to receive this message.
        Set<JemoApplicationMetaData> appList = getApplicationList().stream()
                .filter(app -> app.getId().startsWith(msg.getPluginId() + "_"))
                .filter(app -> isBatch ? app.getBatches().contains(msg.getModuleClass()) : app.getEvents().contains(msg.getModuleClass()))
                .collect(Collectors.toSet());

        //we need to find the largest version number amongst these applications
        double version =
                appList.stream().mapToDouble(app -> PLUGIN_VERSION(app.getId())).max().orElse(1.0);

        final Optional<JemoApplicationMetaData> appMetaData = appList.stream()
                .filter(app -> app.isEnabled() && PLUGIN_VERSION(app.getId()) == version)
                .findFirst();

        if (!appMetaData.isPresent()) {
            return null;
        }

        Set<JemoModule> moduleSet = appList.stream()
                .filter(app -> app.isEnabled())
                .flatMap(app -> Util.F(null, x -> loadModules(app.getId()).stream()))
                .filter(m -> m.getModule().getClass().getName().equals(msg.getModuleClass()))
                .filter(m -> msg.getPluginVersion() != 0 ? m.getMetaData().getVersion() == msg.getPluginVersion() : m.getMetaData().getVersion() == version)
                .collect(Collectors.toSet());

        JemoModule module = moduleSet.stream().limit(1).findAny().orElse(null);
        if (module != null) {
            return process(module, (isBatch ? null : msg), null, null);
        } else {
            throw new RuntimeException(String.format("Module: %d - %s could not be found", msg.getPluginId(), msg.getModuleClass()));
        }
    }

    public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
        final String fBasePath = request.getServletPath();
        String vModulePath = moduleEndpointMap.keySet().parallelStream().filter((k) -> {
            return fBasePath.toUpperCase().startsWith(k.toUpperCase());
        }).findAny().orElse(null);
        String vBasePath = fBasePath;
        if (vModulePath == null) {
            //lets look for a virtual host mapping.
            String url = request.getRequestURL().toString();
            url = url.substring(url.indexOf(":") + 1);
            Pattern p = Pattern.compile("(\\/\\/)([^:]+)(\\:[0-9]+)(\\/.*)");
            Matcher m = p.matcher(url);
            if (m.find()) {
                url = m.group(1) + m.group(2) + m.group(4);
            }
            final String fUrl = url;
            vModulePath = virtualHostMap.entrySet().stream().filter(e -> fUrl.startsWith(e.getKey())).findFirst().orElse(new HashMap.SimpleEntry<>(null, null)).getValue();
            if (vModulePath != null) {
                vBasePath = vModulePath;
            }
        }
        final String modulePath = vModulePath;
        final String basePath = vBasePath;
        if (modulePath != null) {
            String moduleJar = moduleEndpointMap.get(modulePath);
            if (moduleJar != null) {
                //so at this point we need to see if we have loaded this module up yet. we will be able to tell
                //if the module is contained in the module map. but there will be complexity in this as well
                //because if the module is not loaded then we should block until it becomes loaded into the system.

                final Holder<Throwable> error = new Holder<>();
                loadModules(moduleJar).parallelStream().filter((m) -> {
                    if (m.getModule().getBasePath() != null) {
                    	return basePath.toUpperCase()
                    			.startsWith(("/" + PLUGIN_ID(moduleJar) 
                    				+ "/v" + String.valueOf(m.getModule().getVersion()) 
                    				+ (m.getModule().getBasePath().startsWith("/") ? "" : "/") 
                    				+ m.getModule().getBasePath()).toUpperCase()) || 
                    			(PLUGIN_ID(moduleJar) == 0 && basePath.toUpperCase().startsWith(m.getModule().getBasePath().toUpperCase()));
                    }

                    return false;
                }).sorted(new Comparator<JemoModule>() {
                    @Override
                    public int compare(JemoModule o1, JemoModule o2) {
                        return Integer.valueOf(o2.getModule().getBasePath().length()).compareTo(o1.getModule().getBasePath().length());
                    }
                }).limit(1).findFirst().ifPresent((m) -> {
                    try {
                        process(m, null, request, response);
                    } catch (Throwable ex) {
                        error.value = ex;
                    }
                });
                if (error.value != null) {
                    response.sendError(500, JemoError.newInstance(error.value).toString());
                }
            } else {
                //lets make this error response a bit nicer and return a better formatted and smarter looking list of supported plugins with an outline
                //of the endpoints the classes that implement those endpoints a clickable link to the endpoint and a link to view the module documentation
                //if that is available.
                response.sendError(404, "the path: " + modulePath + " does not currespond to any mappings. supported mappings are: " + moduleEndpointMap.toString());
            }
        } else {
            response.sendError(404, "no module mapping defined for: " + basePath + " supported mappings are: " + moduleEndpointMap.toString());
        }
    }

    private static class ModuleExecutionContext {
        private final Module module;
        private final ModuleMetaData metadata;
        private final AbstractJemo server;

        public ModuleExecutionContext() {
            this(null, null, null);
        }

        public ModuleExecutionContext(Module module, ModuleMetaData metadata, AbstractJemo server) {
            this.module = module;
            this.metadata = metadata;
            this.server = server;
        }

        public Module getModule() {
            return module;
        }

        public ModuleMetaData getMetadata() {
            return metadata;
        }

        public AbstractJemo getServer() {
            return server;
        }
    }

    private static final Map<Long, ModuleExecutionContext> MODULE_CONTEXT_MAP = new ConcurrentHashMap<>();

    public static Module getCurrentModule() {
        return MODULE_CONTEXT_MAP.getOrDefault(Thread.currentThread().getId(), new ModuleExecutionContext()).getModule();
    }

    public static ModuleMetaData getCurrentModuleMetaData() {
        return MODULE_CONTEXT_MAP.getOrDefault(Thread.currentThread().getId(), new ModuleExecutionContext()).getMetadata();
    }

    public static AbstractJemo getServerInstance() {
        return MODULE_CONTEXT_MAP.getOrDefault(Thread.currentThread().getId(), new ModuleExecutionContext(null, null, AbstractJemo.DEFAULT_INSTANCE == null ? Jemo.SERVER_INSTANCE : AbstractJemo.DEFAULT_INSTANCE)).getServer();
    }

    public <T extends Object> T runWithModuleContext(Class<T> retval, ManagedFunctionWithException<JemoModule, Object> func) throws Throwable {
        return retval.cast(runWithModule(loadPluginModules(0).iterator().next(), jemoServer.getWORK_EXECUTOR(), func, 600));
    }

    private Object runWithModule(final JemoModule m, final ExecutorService exec, ManagedFunctionWithException<JemoModule, Object> func, final int timeout) throws Throwable {
        Object retval = null;
        try {
            retval = buildModuleFuture(m, exec, func).get(timeout, TimeUnit.SECONDS);
        } catch (TimeoutException tmEx) {
            TIMEOUT_COUNT++;
            long freeMemory = MEMORY_CHECK();
            if (freeMemory < MEMORY_THRESHOLD && TIMEOUT_COUNT >= MAX_TIMEOUT_COUNT) {
                jemoServer.LOG(Level.SEVERE, "Forcing Process Termination because Process Deadlock Detected FreeMem: %d, TimeOutCount: %d", freeMemory, TIMEOUT_COUNT);
                Util.killJVM(0);
            } else {
                jemoServer.LOG(Level.WARNING, "[%d][%s][%s] Possible Worker Deadlock. Worker reset initiatied FREE MEMORY %d MB TIMEOUT %d", m.getMetaData().getId(), String.valueOf(m.getMetaData().getVersion()), m.getMetaData().getName(),
                        freeMemory / 1024, TIMEOUT_COUNT);
                if (TIMEOUT_COUNT >= MAX_TIMEOUT_COUNT) {
                    TIMEOUT_COUNT = 0;
                }
            }
            //timeouts should be re-scheduled.
            retval = tmEx;
        }

        if (retval instanceof Throwable) {
            throw (Throwable) retval;
        }

        return retval;
    }

    private Future<Object> buildModuleFuture(final JemoModule m, final ExecutorService exec, ManagedFunctionWithException<JemoModule, Object> func) {
        return exec.submit(() -> {
            try {
            	Thread.currentThread().setContextClassLoader(m.getClassLoader());
                MODULE_CONTEXT_MAP.put(Thread.currentThread().getId(), new ModuleExecutionContext(m.getModule(), m.getMetaData(), jemoServer));
                return func.applyHandleErrors(m);
            } catch (Throwable ex) {
                jemoServer.LOG(Level.SEVERE, "[%d][%s][%s] Error running module: %s", m.getMetaData().getId(), String.valueOf(m.getMetaData().getVersion()), m.getMetaData().getName(), JemoError.toString(ex));
                return ex;
            } finally {
                MODULE_CONTEXT_MAP.remove(Thread.currentThread().getId());
            }
        });
    }

    private final ConcurrentHashMap<Integer, Long> BatchExecutionMap = new ConcurrentHashMap<>();

    public void clearBatchExecutionMap() {
        BatchExecutionMap.clear();
    }

    private static class ModuleInstanceEventInfo {
        private int numActive = 0;
        private List<Long> activeSince = new ArrayList<>();

        public int getNumActive() {
            return numActive;
        }

        public void setNumActive(int numActive) {
            this.numActive = numActive;
        }

        public List<Long> getActiveSince() {
            return activeSince;
        }

        public void setActiveSince(List<Long> activeSince) {
            this.activeSince = activeSince;
        }
    }

    private static String buildModuleEventStorageKey(final String instanceId, final String moduleClass) {
        return "jemo_event_module_" + moduleClass + "_" + instanceId;
    }

    private String buildModuleEventStorageKey(final String moduleClass) {
        return buildModuleEventStorageKey(jemoServer.getINSTANCE_ID(), moduleClass);
    }

    private static String buildModuleEventStoragePath(final long moduleId, final double moduleVersion) {
        return Jemo.SYSTEM_STORAGE_PATH + "/" + String.valueOf(moduleId) + "_" + String.valueOf(moduleVersion);
    }

    private static synchronized ModuleInstanceEventInfo getModuleEventRuntimeInfo(final String instanceId, final long moduleId, final double moduleVersion, final String moduleClass) {
        ModuleInstanceEventInfo runtimeInfo = CloudProvider.getInstance().getRuntime().read(ModuleInstanceEventInfo.class, buildModuleEventStoragePath(moduleId, moduleVersion), buildModuleEventStorageKey(instanceId, moduleClass));
        if (runtimeInfo == null) {
            runtimeInfo = new ModuleInstanceEventInfo();
        }
        return runtimeInfo;
    }

    private synchronized ModuleInstanceEventInfo getModuleEventRuntimeInfo(final long moduleId, final double moduleVersion, final String moduleClass) {
        return getModuleEventRuntimeInfo(jemoServer.getINSTANCE_ID(), moduleId, moduleVersion, moduleClass);
    }

    public synchronized void writeExecuteModuleEvent(final long moduleId, final double moduleVersion, final String moduleClass) {
        ModuleInstanceEventInfo eventInfo = getModuleEventRuntimeInfo(moduleId, moduleVersion, moduleClass);
        eventInfo.setNumActive(eventInfo.getNumActive() + 1);
        eventInfo.getActiveSince().add(System.currentTimeMillis());
        //remove any events over 30 minutes old
        eventInfo.getActiveSince().removeIf(l -> System.currentTimeMillis() - l > TimeUnit.MINUTES.toMillis(30));
        CloudProvider.getInstance().getRuntime().write(buildModuleEventStoragePath(moduleId, moduleVersion),
                buildModuleEventStorageKey(moduleClass), eventInfo);
    }

    public synchronized void deleteExecuteModuleEvent(final long moduleId, final double moduleVersion, final String moduleClass) {
        ModuleInstanceEventInfo eventInfo = getModuleEventRuntimeInfo(moduleId, moduleVersion, moduleClass);
        eventInfo.setNumActive(eventInfo.getNumActive() - 1);
        eventInfo.getActiveSince().removeIf(l -> System.currentTimeMillis() - l > TimeUnit.MINUTES.toMillis(30));
		/*long min = eventInfo.getActiveSince().stream().mapToLong(l -> l).min().orElse(0);
		eventInfo.getActiveSince().removeIf(l -> l == min); //remove the oldest execution*/
        CloudProvider.getInstance().getRuntime().write(buildModuleEventStoragePath(moduleId, moduleVersion),
                buildModuleEventStorageKey(moduleClass), eventInfo);
		/*if(eventInfo.getNumActive() > 0) {
			
		} else {
			CloudProvider.getInstance().getRuntime().remove(buildModuleEventStoragePath(moduleId, moduleVersion), buildModuleEventStorageKey(moduleClass));
		}*/
    }

    public int getNumModuleEventsRunning(final long moduleId, final double moduleVersion, final String moduleClass) {
        return getNumModuleEventsRunning(jemoServer.getINSTANCE_ID(), moduleId, moduleVersion, moduleClass);
    }

    public long getLastLaunchedModuleEvent(final long moduleId, final double moduleVersion, final String moduleClass) {
        ModuleInstanceEventInfo eventInfo = getModuleEventRuntimeInfo(moduleId, moduleVersion, moduleClass);
        return eventInfo.getActiveSince().stream().max((a1, a2) -> a1.compareTo(a2)).orElse(0l);
    }

    public long getLastLaunchedModuleEventOnGSM(final long moduleId, final double moduleVersion, final String moduleClass) {
        return getActiveLocationList().stream()
                .flatMap(l -> listInstances(l).stream())
                .map(inst -> getModuleEventRuntimeInfo(inst, moduleId, moduleVersion, moduleClass))
                .flatMap(info -> info.getActiveSince().stream())
                .mapToLong(Long::longValue)
                .max().orElse(0);
    }

    /**
     * this method will return the number of actively running events in this instance at this point in time.
     *
     * @param moduleId      the id of the module to get the number of running instances for
     * @param moduleVersion the version of the module to get the number of running instances for
     * @param moduleClass   the class implementation of the module to get the number of running instances for.
     * @return the number of currently active running event processes at this point in time.
     */
    public static int getNumModuleEventsRunning(final String instanceId, final long moduleId, final double moduleVersion, final String moduleClass) {
        ModuleInstanceEventInfo eventInfo = getModuleEventRuntimeInfo(instanceId, moduleId, moduleVersion, moduleClass);
        return eventInfo.getNumActive();
    }

    public int getNumModuleEventsRunningOnLocation(final long moduleId, final double moduleVersion, final String moduleClass) {
        return getNumModuleEventsRunningOnLocation(jemoServer.getLOCATION(), moduleId, moduleVersion, moduleClass);
    }

    public int getNumModuleEventsRunningOnLocation(final String location, final long moduleId, final double moduleVersion, final String moduleClass) {
        return listInstances(location).stream().mapToInt(inst -> getNumModuleEventsRunning(inst, moduleId, moduleVersion, moduleClass)).sum();
    }

    public int getNumModuleEventsRunningOnGSM(final long moduleId, final double moduleVersion, final String moduleClass) {
        return getActiveLocationList().stream()
                .flatMap(l -> listInstances(l).stream())
                .mapToInt(inst -> getNumModuleEventsRunning(inst, moduleId, moduleVersion, moduleClass)).sum();
    }

    private JemoMessage process(final JemoModule m, final JemoMessage msg, final HttpServletRequest request, final HttpServletResponse response) throws Throwable {
        //we should probably run this in a specific thread
        //where we will inject a context variable which contains information about the plugin that will be running the code in the plugin.
        //the call will block until the module execution completes however we will use a future mechansim to get at the value and return it.
        //the key here will be access to the plugin context always so we can make the execution environment smarter.
        ExecutorService exec = jemoServer.getWORK_EXECUTOR(); //doesn't really make sense to have a lot of pools because now that this is a cached pool implementation there are no longer any limits.
        int timeoutInSeconds = 60 * 30; //30 minutes (events can run longer)
        boolean isHttp = false;
        if (request != null) {
            timeoutInSeconds = 20;
            isHttp = true;
        } else if (msg == null) {
            timeoutInSeconds = (int) TimeUnit.SECONDS.convert(6, TimeUnit.HOURS); //batch tasks can run for up to 6 hours.
        }
        //the code will make sure plugin loading has priority execution.
        if (m.getMetaData().getId() == 0) {
            timeoutInSeconds = 300;
        }
        long start = System.currentTimeMillis();
        try {
            Object retval = runWithModule(m, exec, (t) -> {
                JemoMessage msgRet = null;
                if (request == null && response == null) {
                    if (msg != null) {
                        //write that we are running to the GSM
                        try {
                            //writeExecuteModuleEvent(m);
                            msg.setCurrentInstance(jemoServer.getINSTANCE_ID());
                            msg.setCurrentLocation(jemoServer.getLOCATION());
                            msgRet = wrapWithTimer(m, (JemoModule module) -> module.getModule().process(msg));
                        } finally {
                            //write that we have stopped running to the GSM.
                            //deleteExecuteModuleEvent(m);
                        }
                    } else {
                   		wrapWithTimer(m, (JemoModule module) -> module.getModule().processBatch(jemoServer.getLOCATION(), Jemo.IS_CLOUD_LOCATION(jemoServer.getLOCATION())));
                    }
                } else {
                	wrapWithTimer(m, (JemoModule module) -> module.getModule().process(request, response));
                }
                return msgRet;
            }, timeoutInSeconds);
            if (retval instanceof JemoMessage) {
                return (JemoMessage) retval;
            }

            return null;
        } catch (Throwable ex) {
			/*if(request == null && response == null && msg != null) {
				deleteExecuteModuleEvent(m);
			}*/
            throw ex;
        } finally {
            long end = System.currentTimeMillis();
            final boolean fIsHttp = isHttp;
            Arrays.asList(SYSTEM_INTERVALS).parallelStream().forEach(interval -> {
                if (fIsHttp)
                    interval.httpRequest(end - start);
                else
                    interval.eventRequest(end - start);
            });
        }
    }
    
    private void wrapWithTimer(JemoModule m, ManagedConsumer<JemoModule> consumer) {
        wrapWithTimer(m, module -> {
            consumer.accept(module);
            return null;
        });
    }

    private <T> T wrapWithTimer(JemoModule m, ManagedFunction<JemoModule, T> func) {
        long start = 0;
        if (!isDefaultModule(m.getMetaData())) {
            start = System.currentTimeMillis();
        }

        final T result = func.apply(m);

        if (!isDefaultModule(m.getMetaData())) {
            final long duration = System.currentTimeMillis() - start;
            KNOWN_APPLICATIONS.get(m.getMetaData().getPluginJar()).getStats()
                    .compute(m.getModule().getClass().getName(), (k, v) -> {
                        if (v == null) {
                            v = new Accumulator();
                        }
                        return v.add(duration);
                    });
        }

        return result;
    }

    private boolean isDefaultModule(ModuleMetaData metaData) {
        return metaData.getName().equals(PluginManagerModule.class.getSimpleName()) ||
                metaData.getName().equals(DeploymentHistoryModule.class.getSimpleName()) ||
                metaData.getName().equals(ModulesStatsModule.class.getSimpleName());
    }

    @Deprecated
    public static synchronized JemoPluginManager getInstance() {
        return Jemo.SERVER_INSTANCE.getPluginManager();
    }

    public static Set<String> getClassList(String jarFileName, InputStream dataStream) throws IOException {
        return getClassList(jarFileName, dataStream, false);
    }

    public static Set<String> getClassList(String jarFileName, InputStream dataStream, boolean replace) throws IOException {
        List<String> classList = (replace ? null : CloudProvider.getInstance().getRuntime().retrieve(jarFileName + ".classlist", List.class));
        if (classList == null) {
            if (dataStream == null) {
                CloudBlob blob = CloudProvider.getInstance().getRuntime().getModule(jarFileName);
                dataStream = blob.getDataStream();
            }
            classList = getClassList(dataStream).stream().collect(Collectors.toList());
            List<String> jarClassList = classList.parallelStream()
                    .filter(cls -> !cls.startsWith("org.apache")
                            && !cls.startsWith("com.sun")
                            && !cls.startsWith("javax.")
                            && !cls.startsWith("org.postgresql")
                            && !cls.startsWith("com.microsoft")
                            && !cls.startsWith("net.sourceforge"))
                    .collect(Collectors.toList());
            CloudProvider.getInstance().getRuntime().store(jarFileName + ".classlist", jarClassList);
            classList = jarClassList;
        }

        return classList.stream().collect(Collectors.toSet());
    }

    public static Set<String> getClassList(InputStream in) throws IOException {
        HashSet<String> classList = new HashSet<>();
        try (JarInputStream jarIs = new JarInputStream(in)) {
            JarEntry jEntry = null;
            while ((jEntry = jarIs.getNextJarEntry()) != null) {
                if (jEntry.getName().endsWith(".class")) {
                    String className = jEntry.getName().substring(0, jEntry.getName().lastIndexOf('.')).replace('/', '.');
                    classList.add(className);
                }
            }
        }

        return classList;
    }

    public static Set<String> getClassList(byte[] jarBytes) throws IOException {
        return getClassList(new ByteArrayInputStream(jarBytes));
    }

    public static class PluginManagerModule implements Module {

        Logger log = null;
        int pluginId = 0;
        AbstractJemo jemoServer;
        JemoModuleDocumentation documentation;
        JemoRuntimeStatistics runtimeStats;

        public PluginManagerModule(final AbstractJemo jemoServer) {
            this.jemoServer = jemoServer;
            this.documentation = new JemoModuleDocumentation(jemoServer);
            this.runtimeStats = new JemoRuntimeStatistics(jemoServer);
        }

        @Override
        public void construct(Logger logger, String name, int id, double version) {
            this.log = logger;
            this.pluginId = id;
        }

        @Override
        public void start() {
        }

        @Override
        public void stop() {
        }

        private JemoUser authorise(HttpServletRequest request) throws Throwable {
            String authHeader = request.getHeader("Authorization");
            if (authHeader != null) {
                String[] authPart = new String(Base64.getDecoder().decode(authHeader.split(" ")[1]), "UTF-8").split(":");
                String username = authPart[0];
                String password = authPart.length == 1 ? null : authPart[1];
                if (username != null && password != null) {
                    JemoUser user = JemoAuthentication.getUser(username);
                    if (user != null && user.getPassword().equals(Util.md5(password))) {
                        return user;
                    }
                }
            }

            return null;
        }

        private String buildPluginFileName(String strPluginId, String strPluginName, String strPluginVersion) {
            return strPluginId + "_" + strPluginName + "-" + strPluginVersion + ".jar";
        }

        @Override
        public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
            //before we allow anyone to proceed we need to check if their username and password is authenticated against the system.
            JemoUser authUser = authorise(request);
            if (!skipAuthorisation(request)) {
                if (authUser == null) {
                    response.setStatus(401);
                    response.getOutputStream().close();
                    return;
                }
            }

            /**
             * forward all authentication or security requests to the security engine.
             */
            if (request.getRequestURI().startsWith("/jemo/authentication")) {
                JemoAuthentication.processRequest(authUser, request, response);
                return;
            }

            /**
             * forward valid requests to the virtual host manager
             */
            if (request.getRequestURI().startsWith("/jemo/virtualhosts")) {
                JemoVirtualHostManager.processRequest(authUser, request, response);
                return;
            }

            if (request.getRequestURI().startsWith("/jemo/docs")) {
                documentation.processRequest(authUser, request, response);
                return;
            }

            if (request.getRequestURI().startsWith("/jemo/stats")) {
                runtimeStats.processRequest(authUser, request, response);
                return;
            }

            if (request.getRequestURI().startsWith("/jemo/version")) {
                JemoRuntimeVersion.processRequest(authUser, request, response);
                return;
            }

            if (request.getRequestURI().startsWith(JEMO_SETUP)) {
                JemoRuntimeSetup.processRequest(jemoServer, request, response);
                return;
            }

            if (request.getRequestURI().startsWith(JEMO_ADMIN)) {
                JemoRuntimeAdmin.processRequest(this, authUser, request, response);
                return;
            }

            //the manager interface will expect a plugin to have been uploaded as a multi-part form-data part, this first part will always contain the plugin
            //the upload of a plugin will contain user credentials send with Basic authentication.
            //the required fields for this process will be the following:
            //1. PLUGIN: should contain the binary of the plugin jar
            //2. ID: should contain the id of a plugin which belongs to the authenticated user.
            //3. NAME: the name of the plugin
            //4. VERSION: the version of this plugin
            //if these fields are not present then the plugin POST request will not be accepted.
            if ("POST".equals(request.getMethod())) {
                if (request.getContentType().contains("multipart/form-data")) {
                    //all plugin update events should be added to an audit log, which will indicate who made the request what plugin it was for and whether it was authorised or not.
                    Part pluginData = request.getPart("PLUGIN");
                    Part pluginId = request.getPart("ID");
                    Part pluginName = request.getPart("NAME");
                    Part pluginVersion = request.getPart("VERSION");
                    if (pluginData == null || pluginId == null || pluginName == null || pluginVersion == null) {
                        response.setStatus(400);
                    } else {
                        //before we allow an upload we must make sure the user is an administrator or is part of a group which is allowed to deploy to this plugin id.
                        //we need to check if the plugin id is in the range this user can access.
                        final String strPluginId = Jemo.toString(pluginId.getInputStream());
                        int iPluginId = Integer.parseInt(strPluginId);
                        final String strPluginVersion = Jemo.toString(pluginVersion.getInputStream());
                        final String strPluginName = Jemo.toString(pluginName.getInputStream());
                        String pluginFile = buildPluginFileName(strPluginId, strPluginName, strPluginVersion);

                        if (!authUser.isAdmin()) {
                            authUser.extractGroups();
                            if (!authUser.getGroups().stream().anyMatch(g -> ((g.getLocations() != null && g.getLocations().contains(jemoServer.getLOCATION()))
                                    || (g.getLocationPattern() != null && Pattern.matches(g.getLocationPattern(), jemoServer.getLOCATION())))
                                    && (iPluginId >= g.getModule_id_start_range() && iPluginId <= g.getModule_id_end_range())
                            )) {
                                response.sendError(401, String.format("You do not have permission to deploy plugin %d to the location %s", iPluginId, jemoServer.getLOCATION()));
                                jemoServer.getLOG_FORMATTER().logEvent(new CloudLogEvent(String.format("[%s][%s][%s][%d][%s][%s] plugin deployment failed because access was denied", JemoPluginManager.class.getSimpleName(), jemoServer.getLOCATION(), authUser.getUsername(),
                                        iPluginId, strPluginVersion, pluginFile)).withModuleId(0).withModuleName("JEMO-AUDIT-LOG"));
                                return;
                            }
                        }


                        //we need to check if the pluginFile is already stored in our bucket, if it is then this is an upgrade candidate if it is a module, we also need to check if the jar is a module
                        //a module is defined as a jar which has at least 1 class within it which implements the Module interface.

                        //we need to get a list of the files in this jar file
                        uploadPlugin(iPluginId, Double.parseDouble(strPluginVersion), strPluginName, pluginData.getInputStream(), authUser);
                    }
                } else if (request.getContentType().equalsIgnoreCase("application/json")) {
                    //by posting to the plugin manager you can also set plugin focused parameters. These parameters will have to be set by sending the following parameters.
                    /**
                     * content-type: should be application/json
                     * ID: the plugin id to apply the parameters too.
                     *
                     * BODY: the body should contain a JSON document which is built as follows
                     * { "parameters":[
                     *    {
                     *		"key" : "parameter name",
                     *		"value" : "parameter value",
                     *		"operation" : "upsert | delete" default = upsert
                     *  }
                     * ]}
                     *
                     * parameters will actually be stored in DynamoDB. When parameters are updated through this web service a notification is sent through the
                     * plugin manager which will call the necessary method on the module which will set through a map containing the new parameters that have been set.
                     */
                    if (request.getParameter("ID") != null && Util.parse(request.getParameter("ID")) != 0) {
                        int pluginId = Util.parse(request.getParameter("ID"));
                        if (!authUser.isAdmin()) {
                            authUser.extractGroups();
                            if (!authUser.getGroups().stream().anyMatch(g -> ((g.getLocations() != null && g.getLocations().contains(jemoServer.getLOCATION()))
                                    || (g.getLocationPattern() != null && Pattern.matches(g.getLocationPattern(), jemoServer.getLOCATION())))
                                    && (pluginId >= g.getModule_id_start_range() && pluginId <= g.getModule_id_end_range())
                            )) {
                                response.sendError(401, String.format("You do not have permission to change the configuration of the plugin %d using the location %s", pluginId, jemoServer.getLOCATION()));
                                return;
                            }
                        }
                        ModuleConfiguration config = Jemo.fromJSONString(ModuleConfiguration.class, Util.toString(request.getInputStream()));

                        if (config != null && !config.getParameters().isEmpty()) {
                            CloudProvider.getInstance().getRuntime().setModuleConfiguration(pluginId, config);

                            //now we also want to propagate all of these parameters to other instances so they may become aware of the changes to the settings for the modules associated
                            //to this plugin.
                            jemoServer.getPluginManager().notifyConfigurationChange(pluginId);
                        } else {
                            response.setStatus(400); //bad parameters
                        }
                    } else {
                        response.setStatus(400); //bad parameters
                    }
                } else {
                    response.setStatus(422);
                }
            } else if ("GET".equals(request.getMethod())) {
                //if this is the case then we should provide a maven library download facility for our libraries, the get request should start by asking to download a pom.xml file.
                //here there is security in-place so first of all we have to authenticate with a valid Jemo user and then we will only be able to access libraries or modules which
                //are associated to our credentials
                if (request.getRequestURI().equals("/jemo/check")) {
                    response.setContentType("text/html");
                    //here we should check the status of a specific internal service if requested. (only for the http protocol)
                    String internalUrl = request.getParameter("ick");
                    if (internalUrl == null) {
                        long freeMemory = MEMORY_CHECK();
                        if (freeMemory < 100000) {
                            response.sendError(500, "Dangerously low memory level");
                        } else {
                            byte[] responseBytes = ("<html><body><h1>OK - FREE MEMORY [" + String.valueOf(freeMemory) + "]<h1></body></html>").getBytes("UTF-8");
                            response.setContentLength(responseBytes.length);
                            try (OutputStream out = response.getOutputStream()) {
                                out.write(responseBytes);
                                out.flush();
                            }
                        }
                    } else {
                        HttpClient httpClient = HttpClients.custom()
                                .setDefaultRequestConfig(RequestConfig.custom()
                                        .setCircularRedirectsAllowed(false)
                                        .setConnectTimeout(1000)
                                        .setConnectionRequestTimeout(1000)
                                        .setSocketTimeout(1000)
                                        .build())
                                .setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
                                .setSSLContext(new SSLContextBuilder().loadTrustMaterial(new TrustStrategy() {
                                    public boolean isTrusted(X509Certificate[] xcs, String string) throws CertificateException {
                                        return true;
                                    }
                                }).build()).build();
                        HttpGet getMethod = new HttpGet(internalUrl);
                        HttpResponse checkResponse = httpClient.execute(getMethod);
                        response.setStatus(checkResponse.getStatusLine().getStatusCode());
                        checkResponse.getEntity().writeTo(response.getOutputStream());
                    }
                } else {
                    //the GET method will also contain 2 jolly libraries for Jemo, one which will contain the module interface which allows for the development of modules.
                    //the second will contain the code for the maven plugin which allows new modules to be uploaded to Jemo.
                    if (request.getRequestURI().endsWith(".pom")) {
                        response.setContentType("text/xml");
                        //this means we are looking for a maven file which describes a library which we would like to use in our
                        //project. we can upload libraries to Jemo and consume and include them in our projects this way.
                        try (OutputStream out = response.getOutputStream()) {
                            out.write(buildPOM(request.getRequestURI()));
                            out.flush();
                        }
                    } else if (request.getRequestURI().endsWith(".pom.sha1")) {
                        response.setContentType("text/plain");

                        try (OutputStream out = response.getOutputStream()) {
                            out.write(DigestUtils.sha1Hex(buildPOM(request.getRequestURI().substring(0, request.getRequestURI().lastIndexOf('.')))).getBytes("UTF-8"));
                            out.flush();
                        }
                    } else if (request.getRequestURI().endsWith(".pom.md5")) {
                        response.setContentType("text/plain");

                        try (OutputStream out = response.getOutputStream()) {
                            out.write(DigestUtils.md5Hex(buildPOM(request.getRequestURI().substring(0, request.getRequestURI().lastIndexOf('.')))).getBytes("UTF-8"));
                            out.flush();
                        }
                    } else if (request.getRequestURI().endsWith(".jar")) {
                        response.setContentType("application/jar");

                        byte[] jarBytes = buildJAR(request.getRequestURI());
                        if (jarBytes != null) {
                            try (OutputStream out = response.getOutputStream()) {
                                out.write(jarBytes);
                                out.flush();
                            }
                        } else {
                            response.sendError(404);
                        }
                    } else if (request.getRequestURI().endsWith(".jar.sha1")) {
                        response.setContentType("text/plain");

                        try (OutputStream out = response.getOutputStream()) {
                            out.write(DigestUtils.sha1Hex(buildJAR(request.getRequestURI().substring(0, request.getRequestURI().lastIndexOf('.')))).getBytes("UTF-8"));
                            out.flush();
                        }
                    } else if (request.getRequestURI().endsWith(".jar.md5")) {
                        response.setContentType("text/plain");

                        try (OutputStream out = response.getOutputStream()) {
                            out.write(DigestUtils.md5Hex(buildJAR(request.getRequestURI().substring(0, request.getRequestURI().lastIndexOf('.')))).getBytes("UTF-8"));
                            out.flush();
                        }
                    } else {
                        log.log(Level.INFO, "[MAVEN ADAPTER] - I was asked to download: %s", request.getRequestURI());
                        response.sendError(404);
                    }
                }
            } else {
                response.sendError(405);
            }
        }

        private boolean skipAuthorisation(HttpServletRequest request) {
            return ("GET".equals(request.getMethod()) && !request.getRequestURI().startsWith(JEMO_PLUGINS))
                    || "OPTIONS".equals(request.getMethod())
                    || request.getRequestURI().startsWith(JEMO_SETUP);
        }

        public static void respondWithJson(int statusCode, HttpServletResponse response, Object obj) throws IOException {
            response.setStatus(statusCode);
            response.addHeader("Access-Control-Allow-Origin", "*");
            response.setContentType("application/json");
            final OutputStream out = response.getOutputStream();
            final String json = Jemo.toJSONString(obj);
            out.write(json.getBytes(StandardCharsets.UTF_8));
            out.flush();
            out.close();
        }

        public void uploadPlugin(int pluginId, double pluginVersion, String pluginName, InputStream pluginDataStream) throws Throwable {
            uploadPlugin(pluginId, pluginVersion, pluginName, pluginDataStream, null);
        }

        public void uploadPlugin(int pluginId, double pluginVersion, String pluginName, InputStream pluginDataStream, JemoUser authUser) throws Throwable {
            final String pluginFile = buildPluginFileName(String.valueOf(pluginId), pluginName, String.valueOf(pluginVersion));
            File tmpUploadFile = File.createTempFile(pluginFile, UUID.randomUUID().toString() + ".upload");
            try {
                if (authUser == null) {
                    //we should retrieve the first administrator from our repository of users.
                    authUser = JemoAuthentication.getDefaultAdminUser();
                }
                Jemo.stream(new FileOutputStream(tmpUploadFile), pluginDataStream, true);

                Set<String> classList = getClassList(pluginFile, new FileInputStream(tmpUploadFile), true);
                if (!classList.isEmpty()) {
                    CloudProvider.getInstance().getRuntime().uploadModule(pluginFile, new FileInputStream(tmpUploadFile), tmpUploadFile.length());

                    //we now need run any events that are registered.
                    List<String> deadEventListeners = new ArrayList<>();
                    List<String> validQueueIds = CloudProvider.getInstance().getRuntime().listQueueIds(null);
                    List<ModuleEventListener> uploadModuleListeners = jemoServer.getPluginManager().eventListeners.getOrDefault(EVENT_MODULE_UPLOAD, new ArrayList<>());
                    uploadModuleListeners.stream().distinct().forEach((el) -> {
                        //check if the location actually exists before sending the message.
                        if (validQueueIds.contains(el.getLocation())) {
                            JemoMessage msg = new JemoMessage();
                            msg.setPluginId(el.getPluginId());
                            msg.setModuleClass(el.getModuleClass());
                            msg.getAttributes().put("jarFile", pluginFile);
                            msg.getAttributes().put("pluginId", String.valueOf(pluginId));
                            msg.getAttributes().put("pluginVersion", String.valueOf(pluginVersion));
                            msg.getAttributes().put("pluginName", pluginName);
                            msg.getAttributes().put("classList", classList);
                            msg.getAttributes().put("url", "http://localhost:" + jemoServer.getJEMO_HTTP_PORT() + "/jemo/org/eclipse/jemo/" + pluginName + "/" + pluginVersion + "/" + pluginId + "_" + pluginName + "-" + pluginVersion + ".jar");
                            msg.send(el.getLocation()); //send back the result of the request.
                        } else {
                            deadEventListeners.add(el.getLocation());
                        }
                    });
                    uploadModuleListeners.removeIf(el -> deadEventListeners.contains(el.getLocation()));
                    synchronized (jemoServer.getPluginManager().eventListeners) {
                        uploadModuleListeners = jemoServer.getPluginManager().eventListeners.getOrDefault(EVENT_MODULE_UPLOAD, new ArrayList<>());
                        jemoServer.getPluginManager().eventListeners.put(EVENT_MODULE_UPLOAD, uploadModuleListeners.stream().distinct().collect(Collectors.toList()));
                    }
                    deadEventListeners.clear();
                    validQueueIds.clear();
                }

                try (JemoClassLoader jemoLoader = new JemoClassLoader(UUID.randomUUID().toString(), new FileInputStream(tmpUploadFile), JemoClassLoader.class.getClassLoader())) {
                    //on upload it will make sense to save the list of Jemo modules somewhere for future use.
                    //we need to update the crc of the new file here.
                    CloudProvider.getInstance().getRuntime().store(pluginFile + ".crc32", jemoLoader.getCRC32());
                    List<String> jemoModuleList = jemoServer.getPluginManager().MODULE_LIST(classList, jemoLoader);
                    if (!jemoModuleList.isEmpty()) {
                    	//we need to initialise the class loader with all of the needed references to enable microprofile configuration.
                    	jemoServer.getPluginManager().prepareApplicationClassLoader(jemoLoader, pluginId, pluginVersion, null);
                    	//if this jar contains active Jemo modules we need to validate those modules to ensure they are ok.
                    	validateApplication(jemoLoader,jemoModuleList);
                    	
                        //so the first thing we should be doing here is producing the new metadata for this application and saving it to the cloud
                        JemoApplicationMetaData app = jemoServer.getPluginManager().registerModule(pluginFile, authUser.getUsername(), jemoLoader, jemoModuleList);
                        SystemDB.save(MODULE_METADATA_TABLE, app); //save this metadata

                        //this is pointless
                        //jemoServer.getPluginManager().storeModuleList(); //store the module list so we know what modules are available in this app for the cluster.

                        log.log(Level.INFO, "The Jar %s contains Jemo modules, notify other instances about it so they can process it''s presence accordingly.", pluginFile);
                        //lets save the module list to our module storage unit
                        CloudProvider.getInstance().getRuntime().storeModuleList(pluginFile, jemoModuleList);

                        jemoServer.getLOG_FORMATTER().logEvent(new CloudLogEvent(String.format("[%s][%s][%s][%d][%s][%s] module deployment succeeded. The following module implementations were deployed %s",
                                JemoPluginManager.class.getSimpleName(), jemoServer.getLOCATION(), authUser.getUsername(),
                                pluginId, String.valueOf(pluginVersion), pluginFile, jemoModuleList)).withModuleId(0).withModuleName("JEMO-AUDIT-LOG"));

                        //we will send this message for compatibility with Jemo 2.2 and lower.
                        JemoMessage msg = new JemoMessage();
                        msg.setModuleClass(getClass().getName());
                        msg.setPluginId(this.pluginId);
                        msg.setSourceInstance(jemoServer.getINSTANCE_QUEUE_URL());
                        msg.setSourcePluginId(this.pluginId);
                        msg.getAttributes().put("file", pluginFile);
                        msg.getAttributes().put("ignore", "true");
                        msg.broadcast();

                        //however we should now send a different message announcing that this module has changed
                        msg.getAttributes().clear();
                        msg.getAttributes().put("application_metadata", Util.toJSONString(app));
                        msg.broadcast();

                        jemoServer.getMODULE_BATCH_BLACKLIST().removeIf(m -> m.startsWith(String.valueOf(pluginId) + ":" + String.valueOf(pluginVersion)));
                    } else {
                        jemoServer.getLOG_FORMATTER().logEvent(new CloudLogEvent(String.format("[%s][%s][%s][%d][%s][%s] library installation succeeded.", JemoPluginManager.class.getSimpleName(), jemoServer.getLOCATION(), authUser.getUsername(),
                                pluginId, String.valueOf(pluginVersion), pluginFile)).withModuleId(0).withModuleName("JEMO-AUDIT-LOG"));

                        JemoMessage msg = new JemoMessage();
                        msg.setModuleClass(getClass().getName());
                        msg.setPluginId(this.pluginId);
                        msg.setSourceInstance(jemoServer.getINSTANCE_QUEUE_URL());
                        msg.setSourcePluginId(this.pluginId);
                        msg.getAttributes().put("library", pluginFile);
                        msg.broadcast();
                        log.log(Level.INFO, "The Jar %s does not contain any classes which implement an Jemo module it will be stored as a library", pluginFile);
                    }
                } finally {
                    System.gc(); //force garbage collection to unload unused classes.
                }
            } finally {
                tmpUploadFile.delete();
            }
        }
        
        /**
         * this method will validate this module to make sure that any instantiation actions can be executed correctly
         * before we tell the other servers to consume it.
         * 
         * @param jemoLoader the class loader used to introspect the module
         * @param jemoModuleList the list of jemo modules
         * @throws Throwable if there was an issue with module validation.
         */
        private void validateApplication(JemoClassLoader jemoLoader,List<String> jemoModuleList) throws DeploymentException {
        	Config mpConfig = ConfigProvider.getConfig(jemoLoader);
        	for(String jemoModuleClass : jemoModuleList) {
        		Module mod = createModuleInstance(jemoLoader,jemoModuleClass);
        		List<Field> mpConfigResInjectFields = Util.listFieldsWithAnnotations(mod, Inject.class, ConfigProperty.class);
        		//we now need to apply the defined rules for resource injection into fields.
        		for(Field f : mpConfigResInjectFields) {
        			f.setAccessible(true);
        			ConfigProperty cfgDef = f.getAnnotation(ConfigProperty.class);
        			final Class fieldType = f.getType();
        			final String cfgKey = JemoConfig.getConfigKey(cfgDef, mod, f);
        			final boolean hasConfig = !cfgDef.defaultValue().equals(ConfigProperty.UNCONFIGURED_VALUE) || 
        					StreamSupport.stream(mpConfig.getPropertyNames().spliterator(), false)
        					.anyMatch(c -> c.equals(cfgKey));
        			
        			if(Optional.class.isAssignableFrom(fieldType)) {
       
        			} else if(Provider.class.isAssignableFrom(fieldType)) {
        				if(!hasConfig) {
        					throw new DeploymentException("The module "+jemoModuleClass+" expects the configuration value "+cfgKey
        							+" to be injected into the class but a value for this configuration key has not been set. Deployment has failed");
        				}
        			} else {
        				if(!hasConfig) {
        					throw new DeploymentException("The module "+jemoModuleClass+" expects the configuration value "+cfgKey
        							+" to be injected into the class but a value for this configuration key has not been set. Deployment has failed");
        				}
        			}
        		}
        	}
        }
        
        
        /**
         * this method will create a new instance of a module but will not instantiate or initialise it
         * 
         * @param jemoLoader the jemo class loader from which to pull the version of the class to load
         * @param moduleClassName the name of the module class which should be created from the class loader.
         * @return null if the module cannot be instantiated and an instance of the module if it was created successfully.
         */
        private Module createModuleInstance(JemoClassLoader jemoLoader,String moduleClassName) {
        	try {
                return Module.class.cast(jemoLoader.loadClass(moduleClassName).getConstructor().newInstance());
            } catch (ClassNotFoundException | IllegalAccessException clsNfEx) { //will never happen because we already checked above.
                jemoServer.LOG(Level.WARNING, "[%d][%s][%s] the module could not be loaded because of a class loading error: %s", jemoLoader.getApplicationId(), String.valueOf(jemoLoader.getApplicationVersion()), moduleClassName, clsNfEx.getMessage());
            } catch (NoClassDefFoundError clsErr) {
                jemoServer.LOG(Level.WARNING, "I was unable to initialize the module %s because I could not load a class it is dependant on %s please check that the dependancies are correct in your pom", moduleClassName, clsErr.getMessage());
            } catch (Throwable instEx) {
                jemoServer.LOG(Level.WARNING, "I was unable to initialize the class %s because of the error %s - {%s}", moduleClassName, instEx.getMessage(), JemoError.toString(instEx));
            }
        	
        	return null;
        }

        /**
         * Deletes the plugin with the specified id and version
         *
         * @param pluginId the id of the plugin to delete
         * @param pluginVersion the version of the plugin to delete
         * @param authUser the auth user
         * @return true of the plugin version was deleted, false if it was not found
         * @throws Throwable
         */
        public boolean deletePlugin(int pluginId, double pluginVersion, JemoUser authUser) throws Throwable {
            if (authUser == null) {
                //we should retrieve the first administrator from our repository of users.
                authUser = JemoAuthentication.getDefaultAdminUser();
            }

            final CloudRuntime cloudRuntime = CloudProvider.getInstance().getRuntime();
            Optional<JemoApplicationMetaData> appMetaData = readAppMetadataFromDB().stream()
                    .filter(app -> PLUGIN_ID(app.getId()) == pluginId && PLUGIN_VERSION(app.getId()) == pluginVersion)
                    .findFirst();
            if (!appMetaData.isPresent()) {
                return false;
            }

            final JemoApplicationMetaData app = appMetaData.get();
            final String pluginJarFileName = appMetaData.get().getId();

            jemoServer.getPluginManager().unloadPlugin(pluginJarFileName);
            final List<String> jemoModuleList = jemoServer.getPluginManager().MODULE_LIST(pluginJarFileName);

            SystemDB.delete(MODULE_METADATA_TABLE, app);
            cloudRuntime.storeModuleList(pluginJarFileName, List.of()); //store an empty module list
            jemoServer.getPluginManager().removeApplication(pluginJarFileName);

            jemoServer.getLOG_FORMATTER().logEvent(new CloudLogEvent(String.format("[%s][%s][%s][%d][%s][%s] module undeployment succeeded. The following module implementations were undeployed %s",
                    JemoPluginManager.class.getSimpleName(), jemoServer.getLOCATION(), authUser.getUsername(),
                    PLUGIN_ID(pluginJarFileName), String.valueOf(PLUGIN_VERSION(pluginJarFileName)), pluginJarFileName, jemoModuleList)).withModuleId(0).withModuleName("JEMO-AUDIT-LOG"));

            //we will send this message for compatibility with Jemo 2.2 and lower.
            final JemoMessage msg = new JemoMessage();

            //however we should now send a different message announcing that this plugin has deleted
            msg.getAttributes().put("deleted_application_metadata", Util.toJSONString(app));
            msg.broadcast();

            // Delete the plugin file from cloud storage.
            cloudRuntime.removePluginFiles(pluginJarFileName);

            System.gc(); //force garbage collection to unload unused classes.

            return true;
        }

        public void changeState(JemoApplicationMetaData app, JemoUser authUser) throws Throwable {
            if (!app.isEnabled()) {
                deactivatePlugin(app, authUser);
            } else {
                jemoServer.getPluginManager().loadPlugin(app.getId());
                app.setEnabled(true);
                SystemDB.save(MODULE_METADATA_TABLE, app);

                //we will send this message for compatibility with Jemo 2.2 and lower.
                JemoMessage msg = new JemoMessage();
                msg.setModuleClass(getClass().getName());
                msg.setPluginId(this.pluginId);
                msg.setSourceInstance(jemoServer.getINSTANCE_QUEUE_URL());
                msg.setSourcePluginId(this.pluginId);
                msg.getAttributes().put("file", app.getId());
                msg.getAttributes().put("ignore", "true");
                msg.broadcast();

                //however we should now send a different message announcing that this module has changed
                msg.getAttributes().clear();
                msg.getAttributes().put("application_metadata", Util.toJSONString(app));
                msg.broadcast();
            }
        }

        private void deactivatePlugin(JemoApplicationMetaData app, JemoUser authUser) throws IOException {
            if (authUser == null) {
                //we should retrieve the first administrator from our repository of users.
                authUser = JemoAuthentication.getDefaultAdminUser();
            }

            final String pluginJarFileName = app.getId();
            jemoServer.getPluginManager().unloadPlugin(pluginJarFileName);
            List<String> jemoModuleList = jemoServer.getPluginManager().MODULE_LIST(pluginJarFileName);

            final Optional<JemoApplicationMetaData> optionalApp = jemoServer.getPluginManager().getApplicationList().stream()
                    .filter(metadata -> metadata.getId().equals(pluginJarFileName)).findFirst();
            if (!optionalApp.isPresent()) {
                return;
            }

            app.setEnabled(false);
            SystemDB.save(MODULE_METADATA_TABLE, app);
            jemoServer.getPluginManager().removeApplication(pluginJarFileName);

            jemoServer.getLOG_FORMATTER().logEvent(new CloudLogEvent(String.format("[%s][%s][%s][%d][%s][%s] module undeployment succeeded. The following module implementations were undeployed %s",
                    JemoPluginManager.class.getSimpleName(), jemoServer.getLOCATION(), authUser.getUsername(),
                    PLUGIN_ID(app.getId()), String.valueOf(PLUGIN_VERSION(app.getId())), app.getId(), jemoModuleList)).withModuleId(0).withModuleName("JEMO-AUDIT-LOG"));

            //we will send this message for compatibility with Jemo 2.2 and lower.
            JemoMessage msg = new JemoMessage();

            //however we should now send a different message announcing that this plugin has deleted
            msg.getAttributes().put("deleted_application_metadata", Util.toJSONString(app));
            msg.broadcast();

            System.gc(); //force garbage collection to unload unused classes.
        }

        protected byte[] buildJAR(String requestURI) throws Throwable {
            ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
            String modSpecName = requestURI.substring(requestURI.lastIndexOf('/') + 1, requestURI.lastIndexOf('.'));
            String modVersion = modSpecName.substring(modSpecName.lastIndexOf('-') + 1);
            if (requestURI.equals("/jemo/org/eclipse/jemo/module-api/" + modVersion + "/module-api-" + modVersion + ".jar")) {
                writeAPIJar(byteOut);

                return byteOut.toByteArray();
            } else {
                String modName = modSpecName.substring(0, modSpecName.lastIndexOf('-'));

                Set<String> moduleList = CloudProvider.getInstance().getRuntime().listPlugins();
                String moduleJar = moduleList.stream()
                        .filter((so) -> {
                            return (so.startsWith(modName) && so.endsWith(modVersion + ".jar"));
                        }).findFirst().orElse(null);
                if (moduleJar != null) {
                    return CloudProvider.getInstance().getRuntime().getModule(moduleJar).getData();
                }
            }

            return null;
        }

        protected byte[] buildPOM(String requestURI) throws UnsupportedEncodingException {
            String modSpecName = requestURI.substring(requestURI.lastIndexOf('/') + 1, requestURI.lastIndexOf('.'));
            String modVersion = modSpecName.substring(modSpecName.lastIndexOf('-') + 1);
            if (requestURI.equals("/jemo/org/eclipse/jemo/module-api/" + modVersion + "/module-api-" + modVersion + ".pom")) {
                return getPOM(modVersion).getBytes("UTF-8");
            } else {
                //we should check we have a module name loaded on S3 which looks like the end part of this url.
                String modName = modSpecName.substring(0, modSpecName.lastIndexOf('-'));
                String groupId = requestURI.substring("/jemo/".length(), requestURI.lastIndexOf("/" + modName + "/")).replace('/', '.');
                log.log(Level.FINE, "[MAVEN ADAPTER] - MODULE: %s VERSION %s GROUP_ID %s", new Object[]{modName, modVersion, groupId});
                return getPOM(modName, modVersion, groupId).getBytes("UTF-8");
            }
        }

        private void updateApplicationRuntimeVariablesOnUpdate(final String jarFileName, final JemoApplicationMetaData app) {
            synchronized (jemoServer.getPluginManager().KNOWN_APPLICATIONS) {
            	jemoServer.getPluginManager().KNOWN_APPLICATIONS.put(app.getId(), app);
            }
            if (jemoServer.getPluginManager().PLUGIN_VALID(jarFileName)) { //only change the registration in the application list if we are authorised to run this application
                jemoServer.getPluginManager().setApplicationMetaData(app);
            }
            jemoServer.getPluginManager().storeModuleList(); //update the module list to reflect the validity of available applications for this instance.
        }

        private void updateApplicationRuntimeVariablesOnDelete(final String jarFileName, final JemoApplicationMetaData app) {
            if (jemoServer.getPluginManager().PLUGIN_VALID(jarFileName)) { //only change the registration in the application list if we are authorised to run this application
                jemoServer.getPluginManager().removeApplication(jarFileName);
            }
            jemoServer.getPluginManager().storeModuleList(); //update the module list to reflect the validity of available applications for this instance.
        }

        @Override
        public JemoMessage process(JemoMessage message) throws Throwable {
            //cc instances will respond to publish messages for modules as they will trigger the class loading scheme within the plugin manager,
            //please note that publish messages will only be sent for modules.
            if (message.getAttributes().containsKey("file") && !message.getAttributes().containsKey("ignore")) { //we will be expecting this from Jemo 2.2 or lower instances
                //1. we need to attempt to unload the jar because it may have already been loaded and if it has
                //then we should unload it so the next time it is actually used the new version will be loaded
                String jarFileName = (String) message.getAttributes().get("file");
                jemoServer.getPluginManager().unloadPlugin(jarFileName);
                documentation.unloadModule(jarFileName);

                //next we are going to build and save the metadata because if it is comming from an old version
                //then older versions will not do this for us.

                try (JemoClassLoader jemoLoader = buildPluginClassLoader(jarFileName)) {
                    List<String> newModuleList = jemoServer.getPluginManager().MODULE_LIST(jarFileName);
                    JemoApplicationMetaData app = jemoServer.getPluginManager().registerModule(jarFileName, "Legacy Jemo version", jemoLoader, newModuleList);
                    SystemDB.save(MODULE_METADATA_TABLE, app); //save this metadata

                    updateApplicationRuntimeVariablesOnUpdate(jarFileName, app);
                }
            } else if (message.getAttributes().containsKey("application_metadata")) {
                JemoApplicationMetaData app = Util.fromJSONString(JemoApplicationMetaData.class, (String) message.getAttributes().get("application_metadata"));
                if (app != null) {
                    jemoServer.getPluginManager().unloadPlugin(app.getId());
                    documentation.unloadModule(app.getId());
                    updateApplicationRuntimeVariablesOnUpdate(app.getId(), app);
                }
            } else if (message.getAttributes().containsKey("deleted_application_metadata")) {
                JemoApplicationMetaData app = Util.fromJSONString(JemoApplicationMetaData.class, (String) message.getAttributes().get("deleted_application_metadata"));
                if (app != null) {
                    jemoServer.getPluginManager().unloadPlugin(app.getId());
                    documentation.unloadModule(app.getId());
                    updateApplicationRuntimeVariablesOnDelete(app.getId(), app);
                }
            } else if (message.getAttributes().containsKey("library")) {
                documentation.unloadModule((String) message.getAttributes().get("library"));
            } else if (message.getAttributes().containsKey("UPDATED_CONFIG")) {
                //we need to grab the configuration from our NoSQL store for this application
                int targetPluginId = (Integer) message.getAttributes().get("UPDATED_CONFIG");
                final Map<String, String> config = jemoServer.getPluginManager().getModuleConfiguration(targetPluginId);
                Set<JemoModule> appModuleList = jemoServer.getPluginManager().loadPluginModules(targetPluginId);
                if(!appModuleList.isEmpty()) {
                	//get a reference to the class loader holding all the modules for this application
	                JemoClassLoader appClassLoader = appModuleList.iterator().next()
	                		.getClassLoader();
	                
	                //apply the configuration to the class loader in-case this application is using the Micro-profile 3.0 configuration
	                appClassLoader.getApplicationConfiguration().setConfigSource(new JemoConfigSource(config));
	                
	                //broadcast the configuration change event to applications implementing the native Jemo interface.
	                appModuleList.parallelStream().forEach((m) -> {
	                    m.getModule().configure(config);
	                });
                }
            } else if (message.getAttributes().containsKey("METADATA") && message.getSourceModuleClass() != null) {
                //a metadata request message is a message from another module which would like a list of the jars that we have loaded and what
                //are the names of the classes in those jars.
                CloudProvider.getInstance().getRuntime().listPlugins().parallelStream().forEach((moduleJar) -> {
                    if (moduleJar.endsWith(".jar")) {
                        try {
                            String moduleSpec = moduleJar.substring(0, moduleJar.lastIndexOf('.'));
                            String pluginId = moduleSpec.substring(0, moduleSpec.indexOf('_'));
                            String pluginVersion = moduleSpec.substring(moduleSpec.lastIndexOf('-') + 1);
                            String pluginName = moduleSpec.substring(moduleSpec.indexOf('_') + 1, moduleSpec.lastIndexOf('-'));
                            Set<String> classList = getClassList(moduleJar, null, false);
                            JemoMessage msg = new JemoMessage();
                            msg.setPluginId(message.getSourcePluginId());
                            msg.setModuleClass(message.getSourceModuleClass());
                            msg.getAttributes().put("jarFile", moduleJar);
                            msg.getAttributes().put("pluginId", pluginId);
                            msg.getAttributes().put("pluginVersion", pluginVersion);
                            msg.getAttributes().put("pluginName", pluginName);
                            msg.getAttributes().put("classList", classList);
                            msg.getAttributes().put("url", "http://localhost:" + jemoServer.getJEMO_HTTP_PORT() + "/jemo/org/eclipse/jemo/" + pluginName + "/" + pluginVersion + "/" + pluginId + "_" + pluginName + "-" + pluginVersion + ".jar");
                            msg.send(message.getSourceInstance()); //send back the result of the request.
                        } catch (IOException ioEx) {
                        }
                    }
                });
            } else if (message.getAttributes().containsKey("EVENT") && message.getSourceModuleClass() != null) {
                List<ModuleEventListener> eventListeners = jemoServer.getPluginManager().eventListeners.get((String) message.getAttributes().get("EVENT"));
                if (eventListeners == null) {
                    eventListeners = new CopyOnWriteArrayList<>();
                    jemoServer.getPluginManager().eventListeners.put((String) message.getAttributes().get("EVENT"), eventListeners);
                }
                if (!eventListeners.stream().anyMatch(el -> el.getPluginId() == message.getSourcePluginId()
                        && el.getModuleClass().equalsIgnoreCase(message.getSourceModuleClass())
                        && el.getLocation().equalsIgnoreCase(message.getSourceInstance()))) {
                    eventListeners.add(new ModuleEventListener(message.getSourcePluginId(), message.getSourceModuleClass(), message.getSourceInstance()));
                }
            } else if (message.getAttributes().containsKey(JemoVirtualHostManager.EVENT_RELOAD)) {
                jemoServer.getPluginManager().loadVirtualHostDefinitions();
            } else if (message.getAttributes().containsKey("START_FIXED_APP") && message.getAttributes().containsKey("START_FIXED_MODULE")) {
                JemoApplicationMetaData metadata = Util.fromJSONString(JemoApplicationMetaData.class, message.getAttributes().get("START_FIXED_APP").toString());
                final String module = message.getAttributes().get("START_FIXED_MODULE").toString();

                Set<JemoModule> moduleList = null;
                do {
                    moduleList = jemoServer.getPluginManager().loadModules(metadata.getId());
                    jemoServer.LOG(Level.INFO, "[%s] FIXED PROCESS: We asked for the list of modules and were given the following: %s", metadata.getId(), moduleList == null ? "null" : moduleList.stream().map(m -> m.getModule().getClass().getName()).collect(Collectors.joining(",")));
                    if (moduleList == null) {
                        Thread.sleep(250); //wait a quarter of a second before retrying to module load.
                    }
                } while (moduleList == null);
                jemoServer.LOG(Level.INFO, "[%s][%s] - A request was made to run the fixed method of the module. The following modules were loaded in the application [%s]", metadata.getId(), module,
                        moduleList.stream().map(m -> m.getModule().getClass().getName()).collect(Collectors.joining(",")));
                //we have a null pointer exception somewhere in this statement.
                moduleList.stream()
                        .filter(m -> m.getModule().getClass().getName().equals(module))
                        .findAny().ifPresent(m -> jemoServer.getPluginManager().startFixedProcess(m, metadata.getId()));
            }

            return null;
        }

        @Override
        public String getBasePath() {
            return "/jemo";
        }

        @Override
        public double getVersion() {
            return 1.0;
        }

        private String getPOM(String module, String version, String groupId) {
            StringBuilder pom = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pom.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">");
            pom.append("<modelVersion>4.0.0</modelVersion>");
            pom.append("<groupId>").append(groupId).append("</groupId>");
            pom.append("<artifactId>").append(module).append("</artifactId>");
            pom.append("<version>").append(version).append("</version>");
            pom.append("<packaging>jar</packaging>");
            pom.append("</project>");
            return pom.toString();
        }

        private String getPOM(String version) {
            StringBuilder pom = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
            pom.append("<project xmlns=\"http://maven.apache.org/POM/4.0.0\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd\">");
            pom.append("<modelVersion>4.0.0</modelVersion>");
            pom.append("<groupId>org.eclipse.jemo</groupId>");
            pom.append("<artifactId>module-api</artifactId>");
            pom.append("<version>").append(version).append("</version>");
            pom.append("<packaging>jar</packaging>");

            pom.append("<dependencyManagement>");
            pom.append("<dependencies>");
            pom.append("<dependency>");
            pom.append("<groupId>com.amazonaws</groupId>");
            pom.append("<artifactId>aws-java-sdk-bom</artifactId>");
            pom.append("<version>1.11.8</version>");
            pom.append("<type>pom</type>");
            pom.append("<scope>import</scope>");
            pom.append("</dependency>");
            pom.append("</dependencies>");
            pom.append("</dependencyManagement>");

            pom.append("<dependencies>");
            pom.append("<dependency>");
            pom.append("<groupId>com.amazonaws</groupId>");
            pom.append("<artifactId>aws-java-sdk</artifactId>");
            pom.append("<version>1.11.8</version>");
            pom.append("</dependency>");

            pom.append("<dependency>");
            pom.append("<groupId>javax.servlet</groupId>");
            pom.append("<artifactId>javax.servlet-api</artifactId>");
            pom.append("<version>3.1.0</version>");
            pom.append("</dependency>");
            pom.append("</dependencies>");
            pom.append("</project>");
            return pom.toString();
        }

        private void writeAPIJar(OutputStream out) throws Throwable {
            Util.createJar(out, Module.class, ModuleInfo.class, WebServiceModule.class, EventModule.class, BatchModule.class, FixedModule.class,
                    JemoMessage.class, JemoError.class, CloudRuntime.class,
                    CloudBlob.class, QueueDoesNotExistException.class, CloudLogEvent.class, CloudQueueProcessor.class,
                    SystemDBObject.class, ModuleConfiguration.class, JemoClassLoader.class, CloudProvider.class,
                    SystemDB.class, Util.class, JemoUser.class, JemoGroup.class, JemoAuthentication.class,
                    ManagedFunctionWithException.class, ManagedAcceptor.class, ManagedConsumer.class,
                    Frequency.class, ModuleLimit.class, ModuleLimit.Builder.class, KeyValue.class);
        }

        @Override
        public void processBatch(String location, boolean isCloudLocation) throws Throwable {
            //we want to make sure this batch is executed only once across the entire GSM.
            //to do this we will write a lock file onto our cloud storage solution which contains the date in which we started the process
            //and before we do this we will read the same file to see if some one else has the lock.
            final CloudRuntime rt = CloudProvider.getInstance().getRuntime();
            Long lockTime = rt.read(Long.class, Jemo.SYSTEM_STORAGE_PATH, Jemo.LOCK_FILE_FIXED_PROCESS);
            if (lockTime == null || System.currentTimeMillis() - lockTime > TimeUnit.MINUTES.toMillis(3)) { //or the lock file does not exist or it is stale (older than 3 minutes).
                rt.write(Jemo.SYSTEM_STORAGE_PATH, Jemo.LOCK_FILE_FIXED_PROCESS, System.currentTimeMillis()); //write the lock file to avoid other processes starting up.

                try {
                    //1. we need to get a comprehensive list of the modules which implement a fixed process.
                    List<JemoApplicationMetaData> appList = readAppMetadataFromDB().stream()
                            .filter(app -> app.isEnabled() && !app.getFixed().isEmpty())
                            .collect(Collectors.toList());

                    //1.5 we need to get a list of all active locations
                    Set<String> locationList = jemoServer.getPluginManager().getActiveLocationList();

                    //2. we need a list of active instances in this location.
                    Map<String, String> instanceLocationMap = locationList.stream()
                            .flatMap(l -> jemoServer.getPluginManager().listInstances(l).stream()
                                    .map(i -> new AbstractMap.SimpleEntry<>(i, l))
                            )
                            .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
                    Set<String> instanceList = instanceLocationMap.keySet();

                    //3. of these applications we need to find out which ones are active. Keep in mind that an active application
                    //will save it's state to S3 at least every minute.

                    List<JemoFixedModuleState> allModuleState = appList.stream() //the module state is the list of effectively active and running fixed modules for this location.
                            .flatMap(app -> instanceList.stream()
                                    .flatMap(inst -> app.getFixed().stream().map(mod -> new AbstractMap.SimpleEntry<>(inst, mod)))
                                    .map(mod -> new JemoFixedModuleState(app, mod.getValue(), rt.read(Long.class, Jemo.SYSTEM_STORAGE_PATH, "lastactive-" + mod.getValue() + "-" + mod.getKey() + "-" + String.valueOf(app.getId())), mod.getKey()))
                            )
                            .collect(Collectors.toList());

                    List<JemoFixedModuleState> activeModuleState = allModuleState.stream()
                            .filter(st -> st.getLastActiveOn() != null && System.currentTimeMillis() - st.getLastActiveOn() < TimeUnit.MINUTES.toMillis(5)) //anything which has not told us it is active within the last 5 minutes is dead
                            .collect(Collectors.toList());

                    //4. we need to build a list which contains the modules which are in a state which is in-compatible with their limits.
                    //these are modules that are either not within the module state list or are in the state list but there are not enough processes running based on the limits.

                    //1. we need to know the target number of processes on a per app/module basis and the limits associated with them.
                    List<JemoFixedModuleState> fixedProcessesToStart = allModuleState.stream()
                            .flatMap(ms -> {
                                List<JemoFixedModuleState> appModuleState = activeModuleState.stream()
                                        .filter(am -> am.getApplication().getId().equals(ms.getApplication().getId())
                                                && am.getModule().equals(ms.getModule()))
                                        .collect(Collectors.toList());

                                //this means that this is not active anywhere and that means that we should generate as many state entries as processes which need to be activated
                                //of course with the right coordinates.
                                ModuleLimit limit = ms.getApplication().getLimits().get(ms.getModule());
                                //we need to know the total number of instances of this process we need. (a value of -1 means ignore)
                                Set<String> validLocationList = locationList.stream().filter(l -> limit.getFixedLocations() == null || Arrays.asList(limit.getFixedLocations()).contains(l)).collect(Collectors.toSet());

                                List<JemoFixedModuleState> newModuleStateList = new ArrayList<>();
                                if (!validLocationList.isEmpty()) {
                                    int totalInstances = limit.getMaxActiveFixedPerGSM() != -1 ? limit.getMaxActiveFixedPerGSM() :
                                            (limit.getMaxActiveFixedPerLocation() != -1 ? limit.getMaxActiveFixedPerLocation() * validLocationList.size() :
                                                    (limit.getMaxActiveFixedPerInstance() != -1 ? limit.getMaxActiveFixedPerInstance() * instanceList.size() : instanceList.size()));

                                    totalInstances -= appModuleState.stream().map(st -> st.getInstance()).count();
                                    jemoServer.LOG(Level.INFO, "[%s] we are missing %d instances in the GSM the size of the instance list is %d the number of active instances is %d", ms.getModule(), totalInstances, instanceList.size(),
                                            appModuleState.stream().map(st -> st.getInstance()).count());
                                    IntStream.range(0, totalInstances)
                                            .boxed()
                                            .forEach(i -> {
                                                //1. get the location with the least number of instances assigned.
                                                Map<String, List<JemoFixedModuleState>> stateByLocation = Stream.concat(newModuleStateList.stream(), appModuleState.stream())
                                                        .collect(Collectors.groupingBy(st -> instanceLocationMap.get(st.getInstance())));

                                                stateByLocation.putAll(validLocationList.stream().filter(l -> !stateByLocation.containsKey(l)).collect(Collectors.toMap(l -> l, l -> new ArrayList<>())));

                                                final String targetLocation = stateByLocation.entrySet()
                                                        .stream()
                                                        .min((e1, e2) -> new Integer(e1.getValue().size()).compareTo(e2.getValue().size()))
                                                        .map(e -> e.getKey())
                                                        .orElse(location);

                                                Map<String, List<JemoFixedModuleState>> stateByInstance = Stream.concat(newModuleStateList.stream(), appModuleState.stream())
                                                        .collect(Collectors.groupingBy(st -> st.getInstance()));

                                                Set<String> validInstanceList = instanceLocationMap.entrySet().stream()
                                                        .filter(e -> e.getValue().equals(targetLocation))
                                                        .map(e -> e.getKey())
                                                        .collect(Collectors.toSet());

                                                stateByInstance.putAll(validInstanceList.stream().filter(inst -> !stateByInstance.containsKey(inst)).collect(Collectors.toMap(inst -> inst, inst -> new ArrayList<>())));

                                                //instance distribution makes sense but only if we are not targeting a fixed number of processes per instance.
                                                String targetInstance = stateByInstance.entrySet()
                                                        .stream()
                                                        .min((e1, e2) -> new Integer(e1.getValue().size()).compareTo(e2.getValue().size()))
                                                        .map(e -> e.getKey())
                                                        .orElse(jemoServer.getINSTANCE_ID());

                                                //once we have a target instance we need to check how many modules we have active in this instance.
                                                final String fTargetInstance = targetInstance;
                                                final long numActiveInTargetInstance = Stream.concat(newModuleStateList.stream(), appModuleState.stream()).filter(app -> app.getInstance().equals(fTargetInstance)).count();
                                                if (limit.getMaxActiveFixedPerInstance() != -1 && numActiveInTargetInstance >= limit.getMaxActiveFixedPerInstance()) {
                                                    //this will make this instance invalid, so we should search for an instance which is under the limit
                                                    targetInstance = instanceLocationMap.entrySet().stream()
                                                            .filter(e -> validLocationList.contains(e.getValue()))
                                                            .filter(e -> Stream.concat(newModuleStateList.stream(), appModuleState.stream()).filter(app -> app.getInstance().equals(e.getKey())).count() < limit.getMaxActiveFixedPerInstance())
                                                            .map(e -> e.getKey())
                                                            .findAny().orElse(null);
                                                }
                                                jemoServer.LOG(Level.FINE, "[%s][%s] GSM Process Distribution %s selected target instance %s", ms.getApplication().getId(), ms.getModule(),
                                                        instanceLocationMap.entrySet().stream()
                                                                .map(e -> String.format("LOCATION: %s INSTANCE:[%s] TOTAL: %d", e.getValue(), e.getKey(), Stream.concat(newModuleStateList.stream(), appModuleState.stream()).filter(app -> app.getInstance().equals(e.getKey())).count()))
                                                                .collect(Collectors.joining(" - ")), targetInstance
                                                );
                                                if (targetInstance != null) {
                                                    JemoFixedModuleState newModuleState = new JemoFixedModuleState(ms.getApplication(), ms.getModule(), null, targetInstance);
                                                    newModuleStateList.add(newModuleState);
                                                }
                                            });
                                    //anything in the resultant new module state will need to be added to the active module state as we expect these new modules to now be active for the purpose of calculation.
                                    activeModuleState.addAll(newModuleStateList);
                                }

                                return newModuleStateList.stream();
                            })
                            .collect(Collectors.toList());

                    //5. now that we know which processes to start and where we should send messages to those processes to tell them to start them up.
                    for (JemoFixedModuleState st : fixedProcessesToStart) {
                        JemoMessage msg = new JemoMessage();
                        msg.setPluginId(0); //send to myself.
                        msg.setModuleClass(getClass().getName()); //target myself as a module
                        msg.setPluginVersion(1.0); //set to myself
                        msg.getAttributes().put("START_FIXED_APP", Util.toJSONString(st.getApplication()));
                        msg.getAttributes().put("START_FIXED_MODULE", st.getModule());
                        msg.send(CloudProvider.getInstance().getRuntime().getQueueId("JEMO-" + instanceLocationMap.get(st.getInstance()) + "-" + st.getInstance()));
                    }
                    jemoServer.LOG(Level.INFO, "Total GSM Instances %d Started Processes %d Fixed Processes as follows: %s", instanceList.size(), fixedProcessesToStart.size(), fixedProcessesToStart.stream()
                            .map(proc -> proc.getApplication().getId() + ":" + proc.getModule() + ":" + proc.getInstance()).collect(Collectors.joining(",")));
                } finally {
                    //remove the lock file. (we should delay this as we definately want to keep the lock for at least 1 minute)
                    rt.remove(Jemo.SYSTEM_STORAGE_PATH, Jemo.LOCK_FILE_FIXED_PROCESS);
                }
            } else {
                jemoServer.LOG(Level.INFO, "Batch process request ignored because lock file is present");
            }
        }

        @Override
        public ModuleLimit getLimits() {
            return ModuleLimit.newInstance()
                    .setMaxActiveBatchesPerGSM(1)
                    .build();
        }

    }

    public static List<JemoApplicationMetaData> readAppMetadataFromDB() {
        return SystemDB.list(MODULE_METADATA_TABLE, JemoApplicationMetaData.class);
    }

    public Map<String, String> getModuleConfiguration(int pluginId) {
        return CloudProvider.getInstance().getRuntime().getModuleConfiguration(pluginId);
    }

    public static JemoClassLoader buildPluginClassLoader(String pluginJar) throws IOException {
        //documentation for something which is not a module is being requested, this means we will likely have to download from our code storage
        Long moduleCRC32 = CloudProvider.getInstance().getRuntime().retrieve(pluginJar + ".crc32", Long.class);
        JemoClassLoader modClassLoader = null;
        if (moduleCRC32 == null || moduleCRC32 == 0) {
        	moduleCRC32 = null;
            CloudBlob modBlob = CloudProvider.getInstance().getRuntime().getModule(pluginJar);
            if (modBlob != null) {
                try (InputStream data = modBlob.getDataStream()) {
                    modClassLoader = new JemoClassLoader(UUID.randomUUID().toString(), data, Jemo.class.getClassLoader());
                }
            }
        } else {
            modClassLoader = new JemoClassLoader(UUID.randomUUID().toString(), pluginJar, moduleCRC32, Jemo.class.getClassLoader());
        }
        if (moduleCRC32 == null && modClassLoader != null) {
            CloudProvider.getInstance().getRuntime().store(pluginJar + ".crc32", modClassLoader.getCRC32());
        }
        return modClassLoader;
    }

    /**
     * this method will unload a module defined by the jar file name passed.
     *
     * @param jarFileName the name of the jar file to unload.
     */
    public void unloadPlugin(String jarFileName) throws IOException {
        if (LIVE_MODULE_MAP.containsKey(jarFileName)) {
            long start = System.currentTimeMillis();
            Set<JemoModule> moduleSet = LIVE_MODULE_MAP.get(jarFileName);
            Holder<JemoClassLoader> moduleClassLoader = new Holder<>();
            moduleSet.forEach((originalModule) -> {
                try {
                    if (moduleClassLoader.value == null) {
                        moduleClassLoader.value = originalModule.getClassLoader();
                    }
                    runWithModule(originalModule, jemoServer.getWORK_EXECUTOR(), (m) -> {
                        m.getModule().stop();
                        return null;
                    }, 10);
                    originalModule.close(); //shutdown any watchdogs
                } catch (Throwable ex) {
                    ex.printStackTrace();
                } finally {
                	originalModule.setClassLoader(null);
                }
            });
            if (moduleClassLoader.value != null) {
                moduleClassLoader.value.close();
            }
            LIVE_MODULE_MAP.remove(jarFileName);
            jemoServer.LOG(Level.INFO, "[%s][%s] was unloaded from the system successfully. The process took %d (ms)", getClass().getSimpleName(), jarFileName, System.currentTimeMillis() - start);
        }
    }

    /**
     * this method will register a module with the system, this essentially means writing the fact that
     * it exists to our NoSQL data store.
     *
     * @returns true if the module already exists in the system and it's metadata was updated
     * or false if the module did not exist in the system and it was installed as net new.
     */
    private JemoApplicationMetaData registerModule(final String jarFileName, final String username, final JemoClassLoader appClassLoader, final List<String> moduleList)
            throws IOException, ClassNotFoundException, InstantiationException, IllegalAccessException, NoSuchMethodException, InvocationTargetException {
        //1. see if we can find an existing registration for this module in the system.
        JemoApplicationMetaData appMetadata = CloudProvider.getInstance().getRuntime()
                .getNoSQL(MODULE_METADATA_TABLE, jarFileName, JemoApplicationMetaData.class);

        if (appMetadata == null) {
            appMetadata = new JemoApplicationMetaData();
            appMetadata.setId(jarFileName);
            appMetadata.setInstallDate(System.currentTimeMillis());
            appMetadata.setName(PLUGIN_NAME(jarFileName));
            appMetadata.setVersion(PLUGIN_VERSION(jarFileName));
            appMetadata.setEnabled(true);
        } else {
            appMetadata.setLastUpgradeDate(System.currentTimeMillis());
        }
        appMetadata.setLastUploadedBy(username);

        //we now need to discover the details of the modules.
        appMetadata.getBatches().clear();
        appMetadata.getEvents().clear();
        appMetadata.getEndpoints().clear();
        appMetadata.getFixed().clear();
        for (String moduleClass : moduleList) {
            Class cls = appClassLoader.loadClass(moduleClass);
            Module mod = Module.class.cast(cls.getConstructor().newInstance());
            appMetadata.getLimits().put(moduleClass, JemoApplicationMetaData.JemoModuleLimits.wrap(mod.getLimits()));
            addModuleToApplicationMetadata(appMetadata, mod);
        }

        return appMetadata;
    }
    
    private String getModuleEndpoint(JemoApplicationMetaData appMetadata, Module m) {
    	return "/" + PLUGIN_ID(appMetadata.getId()) + "/v" + PLUGIN_VERSION(appMetadata.getId()) + (m.getBasePath().startsWith("/") ? m.getBasePath() : "/" + m.getBasePath());
    }
    
    private void addModuleToApplicationMetadata(JemoApplicationMetaData appMetadata,Module m) {
    	if (JemoModule.implementsWeb(m.getClass())) {
            appMetadata.getEndpoints().put(m.getClass().getName(), getModuleEndpoint(appMetadata, m));
        }
        //does this implement a batch processor there is a way to check.
        if (JemoModule.implementsBatch(m.getClass())) {
            appMetadata.getBatches().add(m.getClass().getName());
        }
        //does this implement an event processor there is a way to check.
        if (JemoModule.implementsEvent(m.getClass())) {
            appMetadata.getEvents().add(m.getClass().getName());
        }
        //does this implement a fixed processor
        if (JemoModule.implementsFixed(m.getClass())) {
            appMetadata.getFixed().add(m.getClass().getName());
        }
    }
    
    /**
     * this method will add metadata and object references to the class loader to identify the application
     * resource context
     * 
     * @param appClassLoader a reference to the class loader where this application is located.
     * @param applicationId the id of the application
     * @param applicationVersion the version of the application
     * @param moduleConfig the configuration of this application, if the value is null then we will retrieve the configuration from within this method.
     */
    protected void prepareApplicationClassLoader(JemoClassLoader appClassLoader,int applicationId,double applicationVersion,Map<String, String> moduleConfig) {
    	appClassLoader.setApplicationConfiguration(new JemoConfig(moduleConfig == null ? getModuleConfiguration(applicationId) : moduleConfig,new MicroProfileConfigSource(appClassLoader)));
    	appClassLoader.setApplicationId(applicationId);
    	appClassLoader.setApplicationVersion(applicationVersion);
    	appClassLoader.setJemoServer(jemoServer);
    }

    /**
     * we generally need to re-think how this is done because it really only makes sense to load
     * the modules physically into memory when they are actually needed and if they have not been used for
     * a period of time they should also be unloaded automatically.
     * <p>
     * we should also split what is done here into two different tasks.
     * 1. registerModule - makes the overall system aware of the modules metadata (these are the actual modules which have been defined)
     * 2. installModule - this will take a new jar file an deploy it onto the system.
     * <p>
     * the changes above will make the overall system more lightweight and will decrease startup times.
     *
     * @param jarFileName the name of the jar module to load into the system.
     * @throws IOException if there was a problem loading or registering the module.
     */
    public void loadPlugin(String jarFileName) throws IOException {
        if (PLUGIN_VALID(jarFileName)) {
            try {
            	final int pluginId = JemoPluginManager.PLUGIN_ID(jarFileName);
            	final double pluginVersion = JemoPluginManager.PLUGIN_VERSION(jarFileName);
            	final JemoApplicationMetaData appMetadata;
            	final Map<String, String> moduleConfig;
                //ok now all that we need to do at this point is load the jar into the JCL and instantiate any modules that
                //are contained within it. We will also shutdown any existing modules if they have already been associated with the plugin id specified.
                List<String> newModuleList = jemoServer.getPluginManager().MODULE_LIST(jarFileName);
                Holder<JemoClassLoader> jemoClassLoaderHolder = new Holder<>();
                Holder<Long> uploadDate = new Holder<>();
                Holder<Long> installDate = new Holder<>();
                if (!newModuleList.isEmpty()) {
                    jemoClassLoaderHolder.value = buildPluginClassLoader(jarFileName);
                    uploadDate.value = jemoClassLoaderHolder.value.getCreatedDate();
                    installDate.value = CloudProvider.getInstance().getRuntime().getModuleInstallDate(jarFileName);
                    PLUGIN_MANAGER_MODULE.documentation.unloadModule(jarFileName);
                    jemoServer.LOG(Level.INFO, "[%s][%s] loading module classes %s", jarFileName, Jemo.logDateFormat.format(new java.util.Date(uploadDate.value)), newModuleList);
                    
                    //prepare the class loader with application metadata.
                    moduleConfig = getModuleConfiguration(pluginId);
                    prepareApplicationClassLoader(jemoClassLoaderHolder.value, pluginId, pluginVersion, moduleConfig);
                    
                    //at this point we will want to update the application metadata. (if we don't already know about it of course)
                    appMetadata = KNOWN_APPLICATIONS.getOrDefault(jarFileName, new JemoApplicationMetaData());
                    if (!KNOWN_APPLICATIONS.containsKey(jarFileName)) {
                        appMetadata.setEnabled(true);
                        appMetadata.setId(jarFileName);
                        appMetadata.setInstallDate(installDate.value == null ? uploadDate.value : installDate.value);
                        appMetadata.setLastUpgradeDate(uploadDate.value);
                        appMetadata.setName(PLUGIN_NAME(jarFileName));
                        appMetadata.setVersion(pluginVersion);
                        KNOWN_APPLICATIONS.put(appMetadata.getId(), appMetadata);
                    }
                } else {
                	//if there are no modules the application really no longer exists.
                	KNOWN_APPLICATIONS.remove(jarFileName);
                	appMetadata = null;
                	moduleConfig = null;
                }
                
                //we should cache the module list here.
                List<Map.Entry<String, String>> endpointList = moduleEndpointMap.entrySet().stream().filter((entry) -> {
                    return entry.getValue().equals(jarFileName);
                }).collect(Collectors.toList());
                endpointList.stream().forEach((entry) -> {
                    moduleEndpointMap.remove(entry.getKey());
                });
                unloadPlugin(jarFileName); //we should un-load the module here.
                final JemoConfig mpConfig;
                if(!newModuleList.isEmpty()) {
                	mpConfig = (JemoConfig)ConfigProvider.getConfig(jemoClassLoaderHolder.value);
                } else {
                	mpConfig = null;
                }
                newModuleList.forEach((cls) -> {
                    //each module will have to be instantiated and stored in the plugin cache for this instance.
                    try {
                        Module mod = PLUGIN_MANAGER_MODULE.createModuleInstance(jemoClassLoaderHolder.value, cls);
                        if(mod != null) {
	                        ModuleMetaData metaData = new ModuleMetaData(pluginId, pluginVersion, mod.getClass().getSimpleName(), jarFileName, getModuleLogger(pluginId, pluginVersion, mod.getClass()));
	                        JemoModule jemoModule = new JemoModule(mod, metaData, jemoClassLoaderHolder.value);
	                        //register the module to receive and process messages during the initialisation phase.
	                        Set<JemoModule> moduleSet = LIVE_MODULE_MAP.get(jarFileName);
	                        if (moduleSet == null) {
	                            moduleSet = new HashSet<>();
	                            LIVE_MODULE_MAP.put(jarFileName, moduleSet);
	                        }
	                        moduleSet.add(jemoModule);
	                        runWithModule(jemoModule, jemoServer.getWORK_EXECUTOR(), (m) -> {
	                        	mod.MODULE_INFO_MAP.put(mod, new Module.ModuleInfo(metaData.getLog(), metaData.getName(), pluginId, pluginVersion));
	                            //we need to get a list of the fields which have the @Inject annotation and the @ConfigProperty annotation.
	                        	List<Field> configFields = Util.listFieldsWithAnnotations(mod, Inject.class, ConfigProperty.class);
	                        	for(Field configField : configFields) {
	                        		configField.setAccessible(true);
	                        		ConfigProperty cfg = configField.getAnnotation(ConfigProperty.class);
	                        		final String cfgKey = JemoConfig.getConfigKey(cfg, mod, configField);
	                        		Class configType = configField.getType();
	                        		if(Optional.class.isAssignableFrom(configField.getType()) || Provider.class.isAssignableFrom(configField.getType())) {
	                        			final ParameterizedType type = (ParameterizedType)configField.getGenericType();
	                        			configType = (Class)type.getActualTypeArguments()[0];
	                        			if(Optional.class.isAssignableFrom(configField.getType())) {
	                        				configField.set(mod, Optional.ofNullable(
	                        						mpConfig.getOptionalValue(cfgKey, configType, 
	                        								cfg.defaultValue().equals(ConfigProperty.UNCONFIGURED_VALUE) ? null : cfg.defaultValue()
	                        						).orElse(null)
	                        					));
	                        			} else {
	                        				configField.set(mod, new JemoProvider(
	                        						mpConfig.getOptionalValue(cfgKey, configType, 
	                        								cfg.defaultValue().equals(ConfigProperty.UNCONFIGURED_VALUE) ? null : cfg.defaultValue()
	                        						).orElse(null)
	                        					));
	                        			}
	                        		} else {
	                        			configField.set(mod, mpConfig.getOptionalValue(cfgKey, configType, 
                								cfg.defaultValue().equals(ConfigProperty.UNCONFIGURED_VALUE) ? null : cfg.defaultValue()
                						).orElse(null));
	                        		}
	                        	}
	                        	mod.construct(metaData.getLog(), metaData.getName(), pluginId, pluginVersion);
	                            if (installDate.value == null) {
	                                mod.installed(); //we should actually only call this if this module version has never been installed.
	                            } else if (installDate.value < uploadDate.value) {
	                                mod.upgraded();
	                            }
	                            //before we start we need to grab the configuration for the module and set it.
	                            mod.configure(moduleConfig);
	                            mod.start();
	                            appMetadata.getLimits().put(cls, JemoApplicationMetaData.JemoModuleLimits.wrap(mod.getLimits()));
	                            return null;
	                        }, 60);
	                        addModuleToApplicationMetadata(appMetadata, mod);
	                        if(JemoModule.implementsWeb(mod.getClass())) {
		                        jemoServer.LOG(Level.INFO, "[%s][%f][%s] will process HTTP/HTTPS/WEBSOCKET requests from the base path: %s", String.valueOf(pluginId), pluginVersion, mod.getClass().getSimpleName(), 
		                        		jemoServer.getPluginManager().getModuleEndpoint(appMetadata, mod));
		                        moduleEndpointMap.put(jemoServer.getPluginManager().getModuleEndpoint(appMetadata, mod), jarFileName);
	                        }
                        }
                    } catch (Throwable instEx) {
                        jemoServer.LOG(Level.WARNING, "I was unable to initialize the class %s because of the error %s - {%s}", cls, instEx.getMessage(), JemoError.toString(instEx));
                    }
                });
                if (!newModuleList.isEmpty()) {
                    CloudProvider.getInstance().getRuntime().setModuleInstallDate(jarFileName, System.currentTimeMillis());
                    System.gc();
                }
            } catch (OutOfMemoryError memErr) {
                jemoServer.LOG(Level.SEVERE, "We have run out of memory. This is a fatal error");
                Util.killJVM(0);
            } catch (RuntimeException rtEx) {
                if (rtEx.getCause() != null && rtEx.getCause() instanceof IOException && rtEx.getCause().getMessage().equals("No space left on device")) {
                    jemoServer.LOG(Level.WARNING, "We have run out of space on disk. This means that functionality may be compromised cleaning temporary directory");
                    //lets clear out the temporary directory to recover space.
                    JemoClassLoader.clearTemporaryFiles();
                } else {
                    jemoServer.LOG(Level.WARNING, "There was an error loading the module %s", JemoError.toString(rtEx));
                }
            }
        }
    }

    /**
     * this method will store a list of running modules for this instance and will contain information as to whether they implement batch processing or not.
     */
    protected void storeModuleList() {
        //once module loading is complete we need to save the list of modules running on this instance.
        //the list should contain the id of the module, the version of the module, the name of the module and if it contains a batch implementation.
        ModuleInfo[] moduleList = getApplicationList().stream()
                .filter(app -> PLUGIN_VALID(app.getId())) //only store modules which would be valid
                .flatMap(app -> Stream.concat(Stream.concat(app.getBatches().stream(), app.getEvents().stream()), app.getEndpoints().keySet().stream())
                        .map(cls -> new ModuleInfo(PLUGIN_ID(app.getId()), PLUGIN_VERSION(app.getId()), PLUGIN_NAME(app.getId()), cls, app.getBatches().contains(cls)))
                )
                .distinct()
                .toArray(ModuleInfo[]::new);
        CloudProvider.getInstance().getRuntime().store(jemoServer.getINSTANCE_ID() + ".modulelist", moduleList);
    }

    public ModuleInfo[] getModuleList(String instanceId) {
        Object modList = CloudProvider.getInstance().getRuntime().retrieve(instanceId + ".modulelist", Jemo.classOf(new ModuleInfo[]{}));
        return modList == null ? new ModuleInfo[]{} : (ModuleInfo[]) modList;
    }

    //we need a method that will give us all of the active instances at a specific location.
    public List<String> listInstances(String location) {
        //we can do this by getting a list of the queue id's and filtering that list based on the location parameter.
        return CloudProvider.getInstance().getRuntime().listQueueIds(location).stream().map(qId -> qId.substring(qId.lastIndexOf('/') + ("JEMO-" + location + "-").length() + 1)).parallel()
                .filter(inst -> isInstanceActive(inst))
                .collect(Collectors.toList());
    }

    protected boolean isInstanceActive(String instanceId) {
        Long lastPollDate = CloudProvider.getInstance().getRuntime().retrieve(instanceId + ".lastpoll", Long.class);
        //if this was at least 5 minutes ago then we can keep it in this list.
        return (lastPollDate != null && System.currentTimeMillis() - TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES) < lastPollDate);
    }

    /**
     * this method will return a reference to the application identified by the module id and version passed.
     * if the version is set to 0 then the most recent version of the module will be returned.
     *
     * @param pluginId the id of the plugin to look for
     * @param version  the version of the module to look for or 0 for the latest version
     * @return a reference to the application metadata for the module and version provided or null if an application which meets the criteria cannot be found.
     */
    public JemoApplicationMetaData getApplication(int pluginId, double version) {
        return getApplicationList().stream()
                .filter(app -> PLUGIN_ID(app.getId()) == pluginId && (version == 0 || PLUGIN_VERSION(app.getId()) == version))
                .max((app1, app2) -> Double.valueOf(PLUGIN_VERSION(app1.getId())).compareTo(PLUGIN_VERSION(app2.getId())))
                .orElse(null);
    }

    public ModuleLimit getModuleLimits(int moduleId, double version, String moduleClass) {
        JemoApplicationMetaData app = getApplication(moduleId, version);
        if (app != null && app.getLimits() != null) {
            return app.getLimits().get(moduleClass);
        }

        return null;
    }

    /**
     * this method will return a list of the currently active and running modules across all of the instances
     * targeted at a specific location in the cluster.
     * <p>
     * Please note this information will be retrieved from the live cluster at maximum every 5 minutes.
     *
     * @param location the location at which to fetch all of the active code modules.
     * @return a list of the modules which have active code running somewhere in the cluster.
     */
    public synchronized Set<ModuleInfo> getLiveModuleList(String location) {
        ModuleInfoCache cachedInfo = LIVE_MODULE_CACHE.get(location);
        if (cachedInfo != null && !cachedInfo.isExpired()) {
            //the cache is valid.
            return cachedInfo.getActiveModules();
        } else {
            CopyOnWriteArraySet<ModuleInfo> activeModuleList = new CopyOnWriteArraySet<>();
            listInstances(location).parallelStream().forEach(inst -> {
                activeModuleList.addAll(Arrays.asList(getModuleList(inst)));
            });
            if (!activeModuleList.isEmpty()) {
                ModuleInfoCache newCache = new ModuleInfoCache(location, activeModuleList);
                LIVE_MODULE_CACHE.put(location, newCache);
                return newCache.getActiveModules();
            } else {
                LIVE_MODULE_CACHE.remove(location);
            }
            return new HashSet<>();
        }
    }

    public synchronized Set<String> getLocationList() {
        return CloudProvider.getInstance().getRuntime().listQueueIds(null, true).stream()
                .filter(q -> q.toUpperCase().endsWith("WORK-QUEUE"))
                .map(q -> q.substring(q.toUpperCase().indexOf(QUEUE_NAME_PREFIX)))
                .map(q -> q.substring(QUEUE_NAME_PREFIX.length(), q.length() - ("WORK-QUEUE".length() + 1)))
                .collect(Collectors.toSet());
    }

    /**
     * this method will return the list of locations available for this GSM.
     * the list of locations will be calculated based on the number of active instances
     * discovered through access to the message queue. we can use the .lastpoll file on S3
     * to discover if an instance is active or not.
     * <p>
     * to constrain resource usage the location list will be stored in a cache for 5 minutes after which
     * it will naturally expire and will be re-calculated if needed.
     *
     * @return a set containing the unique list of active locations in this GSM.
     */
    public synchronized Set<String> getActiveLocationList() {
        return CloudProvider.getInstance().getRuntime().listQueueIds(null).stream()
                .filter(q -> !q.toUpperCase().endsWith("WORK-QUEUE"))
                .map(q -> q.substring(q.toUpperCase().indexOf(QUEUE_NAME_PREFIX)))
                .filter(q -> isInstanceActive(q.substring(q.length() - 36)))
                .map(q -> q.substring(QUEUE_NAME_PREFIX.length(), q.length() - 37))
                .collect(Collectors.toSet());
    }

    public synchronized Set<String> getActiveInstanceList() {
        return CloudProvider.getInstance().getRuntime().listQueueIds(null, false).stream()
                .map(q -> q.substring(q.toUpperCase().indexOf(QUEUE_NAME_PREFIX)))
                .map(q -> q.substring(q.length() - 36))
                .filter(inst -> isInstanceActive(inst))
                .collect(Collectors.toSet());
    }

    public synchronized Set<String> getInstanceLocations(String... instances) {
        final List<String> instanceList = Arrays.asList(instances);
        return CloudProvider.getInstance().getRuntime().listQueueIds(null, false).stream()
                .map(q -> q.substring(q.toUpperCase().indexOf(QUEUE_NAME_PREFIX)))
                .filter(q -> instanceList.contains(q.substring(q.length() - 36)))
                .map(q -> q.substring(QUEUE_NAME_PREFIX.length(), q.length() - 37))
                .collect(Collectors.toSet());
    }

    public synchronized Map<String, String> getInstanceLocationMap(String... instances) {
        final List<String> instanceList = Arrays.asList(instances);
        return CloudProvider.getInstance().getRuntime().listQueueIds(null, false).stream()
                .map(q -> q.substring(q.toUpperCase().indexOf(QUEUE_NAME_PREFIX)))
                .filter(q -> instanceList.contains(q.substring(q.length() - 36)))
                .map(q -> new KeyValue<>(q.substring(q.length() - 36), q.substring(QUEUE_NAME_PREFIX.length(), q.length() - 37)))
                .collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
    }

    /**
     * this method will start the defined fixed process on the module,
     * since we don't expect the process to exit until the module is shutdown
     * we need to wrap this up in a thread so we ensure we return control
     * to the caller of this method immediately. each process will also have a
     * watchdog that will notify that it is still running every 3 minutes
     *
     * @param module a reference to the loaded module to run.
     */
    public synchronized void startFixedProcess(final JemoModule module, final String applicationId) {
        final String watchdogId = UUID.randomUUID().toString();
        final Future<Object> fixedTask = buildModuleFuture(module, jemoServer.getWORK_EXECUTOR(), (mod) -> {
            //write the file that says that we are active.
            CloudProvider.getInstance().getRuntime().write(Jemo.SYSTEM_STORAGE_PATH, "lastactive-" + mod.getModule().getClass().getName() + "-" + jemoServer.getINSTANCE_ID() + "-" + applicationId, System.currentTimeMillis());
            jemoServer.LOG(Level.INFO, "[%s][%s] - STARTING FIXED PROCESS", applicationId, module.getModule().getClass().getName());
            module.getModule().processFixed(jemoServer.getLOCATION(), jemoServer.getINSTANCE_ID());
            module.shutdownWatchdog(watchdogId);
            CloudProvider.getInstance().getRuntime().remove(Jemo.SYSTEM_STORAGE_PATH, "lastactive-" + mod.getModule().getClass().getName() + "-" + jemoServer.getINSTANCE_ID() + "-" + applicationId);
            jemoServer.LOG(Level.INFO, "[%s][%s] - FINISHED FIXED PROCESS", applicationId, module.getModule().getClass().getName());
            return null;
        });
        //we will now monitor this task with a background thread

        final ScheduledFuture watchdog = jemoServer.getSCHEDULER().scheduleAtFixedRate(() -> {
            if (!fixedTask.isDone()) {
                CloudProvider.getInstance().getRuntime().write(Jemo.SYSTEM_STORAGE_PATH, "lastactive-" + module.getModule().getClass().getName() + "-" + jemoServer.getINSTANCE_ID() + "-" + applicationId, System.currentTimeMillis());
            } else {
                module.shutdownWatchdog(watchdogId);
            }
        }, 3, 3, TimeUnit.MINUTES);
        module.addWatchdog(watchdogId, watchdog);
    }
    
    public void notifyConfigurationChange(int applicationId) throws JsonProcessingException {
    	JemoMessage configMsg = new JemoMessage();
    	configMsg.setPluginId(0);
    	configMsg.setModuleClass(PluginManagerModule.class.getName());
    	configMsg.setPluginVersion(1.0);
    	configMsg.setSourcePluginId(0);
    	configMsg.setSourceModuleClass(PluginManagerModule.class.getName());
    	configMsg.setSourcePluginVersion(1.0);
        configMsg.getAttributes().put("UPDATED_CONFIG", applicationId);
        configMsg.broadcast();
    }
}
