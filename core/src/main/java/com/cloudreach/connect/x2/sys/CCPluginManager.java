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
package com.cloudreach.connect.x2.sys;

import com.cloudreach.connect.x2.AbstractX2;
import com.cloudreach.connect.x2.CC;
import com.cloudreach.connect.x2.api.Frequency;
import com.cloudreach.connect.x2.api.KeyValue;
import com.cloudreach.connect.x2.api.Module;
import com.cloudreach.connect.x2.api.ModuleLimit;
import com.cloudreach.connect.x2.internal.model.CCError;
import com.cloudreach.connect.x2.internal.model.CCMessage;
import com.cloudreach.connect.x2.internal.model.CloudBlob;
import com.cloudreach.connect.x2.internal.model.CloudLogEvent;
import com.cloudreach.connect.x2.internal.model.CloudProvider;
import com.cloudreach.connect.x2.internal.model.CloudQueueProcessor;
import com.cloudreach.connect.x2.internal.model.CloudRuntime;
import com.cloudreach.connect.x2.internal.model.ModuleConfiguration;
import com.cloudreach.connect.x2.internal.model.ModuleEventListener;
import com.cloudreach.connect.x2.internal.model.ModuleMetaData;
import com.cloudreach.connect.x2.internal.model.QueueDoesNotExistException;
import com.cloudreach.connect.x2.internal.model.SystemDBObject;
import com.cloudreach.connect.x2.internal.model.X2ApplicationMetaData;
import com.cloudreach.connect.x2.internal.model.X2FixedModuleState;
import com.cloudreach.connect.x2.internal.model.X2Module;
import com.cloudreach.connect.x2.sys.auth.CCAuthentication;
import com.cloudreach.connect.x2.sys.auth.CCGroup;
import com.cloudreach.connect.x2.sys.auth.CCUser;
import com.cloudreach.connect.x2.sys.internal.ManagedAcceptor;
import com.cloudreach.connect.x2.sys.internal.ManagedConsumer;
import com.cloudreach.connect.x2.sys.internal.ManagedFunctionWithException;
import com.cloudreach.connect.x2.sys.internal.SystemDB;
import com.cloudreach.connect.x2.sys.internal.Util;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Modifier;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.Callable;
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
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarInputStream;
import java.util.jar.JarOutputStream;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
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

/**
 * the plugin manager will give a new instance of a plugin based on his id's
 * 
 * essentially the plugin manager is one of the core components of the system
 * and it builds the basis for the micro-kernel architecture of Cloudreach Connect.
 * 
 * The class loader hiearchy should be as follows:
 * 
 * - 1. create an isolated class loader which stems from a system class class loader binded to String.class which
 *      then contains all of the dependant libraries within the plugin context, we will also inject the latest stable
 *			cloudreach connect implementation for the library version.
 * 
 * CC X2 will store libraries on AWS S3 and should allow them to be downloaded through maven directly through
 * a cloudreach connect endpoint.
 * 
 * @author christopher stura
 */
public class CCPluginManager {
	
	public static class MonitoringInterval {
		private String key;
		private long duration;
		private long httpRequests = 0;
		private long eventRequests = 0;
		private long totalHttpTime = 0;
		private long totalEventTime = 0;
		private long intervalStart = System.currentTimeMillis();
		
		public MonitoringInterval(String key,long duration, TimeUnit unit) {
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
			if(System.currentTimeMillis()-intervalStart > duration) {
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
		
		public ModuleInfoCache(String location,Set<ModuleInfo> activeModules) {
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
			return cachedOn+TimeUnit.MINUTES.toMillis(5) < System.currentTimeMillis();
		}
	}
	
	public static String S3_PLUGIN_BUCKET = "CCX2PLUGINLIB".toLowerCase();
	public static String VHOST_KEY = "VIRTUALHOSTS";
	
	public static final String EVENT_MODULE_UPLOAD = "MODULE_UPLOAD";
	
	private final Map<String,Set<X2Module>> LIVE_MODULE_MAP = new ConcurrentHashMap<>();
	private final Map<String,String> moduleEndpointMap = new ConcurrentHashMap<>();
	private final Map<String,List<ModuleEventListener>> eventListeners = new ConcurrentHashMap<>();
	private final Map<String,String> virtualHostMap = new ConcurrentSkipListMap<>((o1,o2) -> new Integer(o1.length()).compareTo(o2.length()) == 0 ? o1.compareTo(o2) : new Integer(o1.length()).compareTo(o2.length())); //a virtual host definition will be mapped to an actual module endpoint.
	private int TIMEOUT_COUNT = 0;
	private static final long MEMORY_THRESHOLD = 100000;
	private static final String MODULE_METADATA_TABLE = "cloudreach_x2_modules";
	private final List<X2ApplicationMetaData> KNOWN_APPLICATIONS = new CopyOnWriteArrayList<>();
	
	/**
	 * this application list will contain the list of applications which are valid to be executed
	 * within this application container.
	 */
	private final List<X2ApplicationMetaData> APPLICATION_LIST = new CopyOnWriteArrayList<>();
	private final Map<String,ModuleInfoCache> LIVE_MODULE_CACHE = new ConcurrentHashMap<>();
	
	private final MonitoringInterval[] SYSTEM_INTERVALS = new MonitoringInterval[] { 
		new MonitoringInterval("5M", 5, TimeUnit.MINUTES), 
		new MonitoringInterval("15M", 15, TimeUnit.MINUTES),
		new MonitoringInterval("30M", 30, TimeUnit.MINUTES),
		new MonitoringInterval("60M", 60, TimeUnit.MINUTES),
		new MonitoringInterval("1D", 1, TimeUnit.DAYS)
	};
	
	private final ExecutorService MODULE_LOADER = Executors.newFixedThreadPool(25); //load 50 modules at the same time.
	private final AbstractX2 x2server;
	private boolean isStartup = true;
	private PluginManagerModule PLUGIN_MANAGER_MODULE = null;
	
	public CCPluginManager(final AbstractX2 x2server) {
		this.x2server = x2server;
		//make sure the plugin buckets exist and if not create them.
		//when we start we will load the virtual host definitions into memory.
		loadVirtualHostDefinitions();
		
		//since each of the items in this bucket will be a jar file, we need to load all of the bytes into a memory area, we will then load these classes
		//seperately based on the plugin needs and configuration. (we will expect the files to follow a naming convention like the following)
		//library name-version.jar if no version is specified then the library will be defaulted to 1.0
		//we want to avoid scanning the S3 bucket for modules and instead we should be picking items up from a dynamo db table instead.
		
		APPLICATION_LIST.addAll(listApplications());
		x2server.LOG(Level.INFO,"[%s] Discovered %d modules %s", getClass().getSimpleName(), APPLICATION_LIST.size(), 
			APPLICATION_LIST.stream()
				.map(app -> app.getId()).collect(Collectors.joining(","))
		);
		
		//we need to check for the CC management application if that does not exist then we should upload it to the S3 bucket in its latest version.
		Logger modLogger = getModuleLogger(0,1.0,PluginManagerModule.class);
		PLUGIN_MANAGER_MODULE = new PluginManagerModule(x2server);
		X2Module defaultPluginManagerModule = new X2Module(PLUGIN_MANAGER_MODULE, new ModuleMetaData(0,1.0,PluginManagerModule.class.getSimpleName(),modLogger));
		HashSet<X2Module> defaultModuleSet = new HashSet<>(Arrays.asList(defaultPluginManagerModule));
		Module pluginManager = defaultPluginManagerModule.getModule();
		pluginManager.construct(defaultPluginManagerModule.getMetaData().getLog(), defaultPluginManagerModule.getMetaData().getName(), defaultPluginManagerModule.getMetaData().getId(), defaultPluginManagerModule.getMetaData().getVersion());
		pluginManager.start();
		LIVE_MODULE_MAP.put("0_PluginManager-1-1.0.jar", defaultModuleSet);
		moduleEndpointMap.put(pluginManager.getBasePath(), "0_PluginManager-1-1.0.jar");
		
		//load on startup is not a good idea instead we are going to lazy load things when they are requested so we can have smaller clusters running more stuff.
		//so in the beginning all we really need is to know what the potential endpoints for the valid modules are.
		APPLICATION_LIST.parallelStream()
			.forEach(app -> {
				moduleEndpointMap.putAll(app.getEndpoints().values().stream()
					.collect(Collectors.toMap(e -> e, e -> app.getId())));
			});
		X2ApplicationMetaData pluginManagerApp = new X2ApplicationMetaData();
		pluginManagerApp.setId("0_PluginManager-1-1.0.jar");
		pluginManagerApp.setEnabled(true);
		pluginManagerApp.setVersion(PLUGIN_MANAGER_MODULE.getVersion());
		pluginManagerApp.setName("PluginManager");
		pluginManagerApp.getEndpoints().put(PLUGIN_MANAGER_MODULE.getClass().getName(), 
			"/0/v"+String.valueOf(PLUGIN_MANAGER_MODULE.getVersion())+(PLUGIN_MANAGER_MODULE.getBasePath().startsWith("/") ? PLUGIN_MANAGER_MODULE.getBasePath() : "/"+PLUGIN_MANAGER_MODULE.getBasePath()));
		pluginManagerApp.getBatches().add(PLUGIN_MANAGER_MODULE.getClass().getName());
		pluginManagerApp.getEvents().add(PLUGIN_MANAGER_MODULE.getClass().getName());
		pluginManagerApp.getLimits().put(PLUGIN_MANAGER_MODULE.getClass().getName(),X2ApplicationMetaData.X2ModuleLimits.wrap(PLUGIN_MANAGER_MODULE.getLimits()));
		
		APPLICATION_LIST.add(pluginManagerApp);
		
		storeModuleList();
		isStartup = false;
	}
	
	/**
	 * this method will lazy load the modules inside a specific application id
	 * 
	 * @param jarFileName the name of the jar file which contains the module to load.
	 * 
	 * @return a list of the X2Modules associated with this application
	 */
	public synchronized Set<X2Module> loadModules(String jarFileName) throws IOException {
		if(!LIVE_MODULE_MAP.containsKey(jarFileName)) {
			x2server.LOG(Level.INFO, "[%s][%s] - I have recieved a request to load the module", getClass().getSimpleName(), jarFileName);
			loadModule(jarFileName); //ask to load the module
		}
		APPLICATION_LIST.stream()
			.filter(app -> app.getId().equals(jarFileName))
			.findFirst().ifPresent(app -> app.setLastUsedOn(System.currentTimeMillis()));
		
		return LIVE_MODULE_MAP.get(jarFileName);
	}
	
	public List<X2ApplicationMetaData> getApplicationList() {
		return APPLICATION_LIST;
	}
	
	public synchronized Set<X2Module> getLoadedModules() {
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
	protected List<X2ApplicationMetaData> listApplications() {
		//step 1: we should see if we have a metadata table already created.
		SystemDB.createTable(MODULE_METADATA_TABLE);
		
		//step 2: we need to check how much data is in here.
		KNOWN_APPLICATIONS.addAll(CloudProvider.getInstance().getRuntime().listNoSQL(MODULE_METADATA_TABLE, X2ApplicationMetaData.class));
		
		if(KNOWN_APPLICATIONS.isEmpty()) {
			//step 3: if we effectively don't have a list of the know applications then we should build one from scratch
			Set<String> fullAppList = CloudProvider.getInstance().getRuntime().listModules();
			SystemDB.save(MODULE_METADATA_TABLE, fullAppList.stream()
				.filter(app -> !KNOWN_APPLICATIONS.parallelStream().anyMatch(x2app -> x2app.getId().equals(app)))
				.map(app -> {
					X2ApplicationMetaData x2app = new X2ApplicationMetaData(); 
					x2app.setId(app);
					x2app.setEnabled(true);
					x2app.setLastUpgradeDate(System.currentTimeMillis()); //the first entry lets use today
					x2app.setLastUsedOn(System.currentTimeMillis());
					x2app.setInstallDate(PLUGIN_INSTALLED_ON(app));
					x2app.setName(PLUGIN_NAME(app));
					x2app.setVersion(PLUGIN_VERSION(app));
					return x2app; 
				}).toArray(X2ApplicationMetaData[]::new)); //this adds newly discovered applications uploaded via non 2.3 versions of X2 for backwards compatibility.
				x2server.LOG(Level.INFO, "[%s] Application List was created successfully", CCPluginManager.class.getSimpleName());
				KNOWN_APPLICATIONS.addAll(CloudProvider.getInstance().getRuntime().listNoSQL(MODULE_METADATA_TABLE, X2ApplicationMetaData.class));
		} 
		
		//return KNOWN_APPLICATIONS.stream().filter(app -> PLUGIN_VALID(app.getId())).collect(Collectors.toList());
		return KNOWN_APPLICATIONS.stream().collect(Collectors.toList()); //we should return all applications because none will be loaded on startup.
	}
	
	public Set<X2Module> getModulesByPluginId(int pluginId) {
		return listModules(pluginId);
	}
	
	public void loadVirtualHostDefinitions() {
		Map<String,String> newVhostMap = CCVirtualHostManager.getVirtualHostDefinitions();
		if(newVhostMap != null) {
			synchronized(virtualHostMap) {
				virtualHostMap.clear();
				virtualHostMap.putAll(newVhostMap);
			}
		}
	}
	
	public Map<String,String> getVirtualHostMap() {
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
		return x2server.PLUGIN_VALID(pluginId);
	}
	
	public static int PLUGIN_ID(String jarFileName) {
		return Integer.parseInt(jarFileName.substring(0,jarFileName.indexOf('_')));
	}
	
	public static double PLUGIN_VERSION(String jarFileName) {
		return Double.parseDouble(jarFileName.substring(jarFileName.lastIndexOf('-')+1,jarFileName.lastIndexOf(".jar")));
	}
	
	public static String PLUGIN_NAME(String jarFileName) {
		return jarFileName.substring(jarFileName.indexOf('_')+1, jarFileName.lastIndexOf('-'));
	}
	
	protected static long PLUGIN_INSTALLED_ON(String jarFileName) {
		try {
			return CloudProvider.getInstance().getRuntime().getModuleInstallDate(jarFileName);
		}catch(IOException ioEx) {
			return System.currentTimeMillis();
		}
	}
	
	protected static long MEMORY_CHECK() {
		long freeMemory = Runtime.getRuntime().freeMemory();
		garbageCollectIfNecessary(freeMemory);
		
		return freeMemory;
	}
	
	protected static boolean garbageCollectIfNecessary(long freeMemory) {
		if(freeMemory < 100000) {
			Runtime.getRuntime().gc();
			return true;
		}
		
		return false;
	}
	
	/**
	 * this method will extract the list of X2 modules given a class loader instance and the
	 * list of classes which are within that class loaders scope.
	 * 
	 * @param classList the list of classes in the class loader scope
	 * @param moduleClassLoader a reference to the class loader responsible for loading the classes in scope
	 * @return a list of the X2 modules managed by the class loader referenced.
	 */
	protected List<String> MODULE_LIST(Collection<String> classList,X2ClassLoader moduleClassLoader) {
		return classList.parallelStream().filter(cls -> {
			try {
				Class classRef = moduleClassLoader.loadClass(cls);
				if(Module.class.isAssignableFrom(classRef) && !Modifier.isAbstract(classRef.getModifiers())) {
					return true;
				}
			}catch(ClassNotFoundException | NoClassDefFoundError | VerifyError | IllegalAccessError clsNfEx) {}
			return false;
		}).collect(Collectors.toList());
	}
	
	protected List<String> MODULE_LIST(String jarFileName,byte[] jarBytes,X2ClassLoader moduleClassLoader) {
		//first lets try and pull the list of modules from our persistance layer.
		ArrayList<String> moduleList = new ArrayList<>();
		try {
			List<String> cachedModuleList = CloudProvider.getInstance().getRuntime().getModuleList(jarFileName);
			if(cachedModuleList == null) {
				//no cache we need to calculate it and if we do we may as well save it back to the cache.
				Set<String> classList = getClassList(jarBytes);
				cachedModuleList = MODULE_LIST(classList, moduleClassLoader);
				CloudProvider.getInstance().getRuntime().storeModuleList(jarFileName, cachedModuleList);
			}
			moduleList.addAll(cachedModuleList);
		}catch(Throwable ex) {
			x2server.LOG(Level.WARNING, "[%s][%s] I was unable to load the list of modules because of the error: %s", 
				new Object[] { CCPluginManager.class.getSimpleName(), jarFileName });
		}
		
		return moduleList;
	}
	
	public static File cacheStreamToFile(InputStream in) throws IOException {
		File tmpFile = File.createTempFile(UUID.randomUUID().toString(), ".stream_cache");
		CC.stream(new FileOutputStream(tmpFile), in, true);
		
		return tmpFile;
	}
	
	protected List<String> MODULE_LIST(String jarFileName) {
		ArrayList<String> moduleList = new ArrayList<>();
		try {
			List<String> cachedModuleList = CloudProvider.getInstance().getRuntime().getModuleList(jarFileName);
			if(cachedModuleList == null) {
				try(X2ClassLoader x2cl = buildModuleClassLoader(jarFileName)) {
					cachedModuleList = MODULE_LIST(x2cl.getClassList(), x2cl);
				}
				CloudProvider.getInstance().getRuntime().storeModuleList(jarFileName, cachedModuleList);
			}
			moduleList.addAll(cachedModuleList);
		}catch(Throwable ex) {
			x2server.LOG(Level.WARNING, "[%s][%s] I was unable to load the list of modules because of the error: %s", 
				new Object[] { CCPluginManager.class.getSimpleName(), jarFileName, CCError.toString(ex) });
		}
		
		return moduleList;
	}
	
	public Logger getModuleLogger(int moduleId, double moduleVersion, Class<? extends Module> mod) {
		Logger logger = Logger.getLogger(moduleId+":"+moduleVersion+":"+mod.getSimpleName());
		logger.setParent(x2server.getSYS_LOGGER());
		return logger;
	}
	
	public Set<X2Module> listModules(int pluginId) {
		return APPLICATION_LIST.stream()
			.filter(app -> PLUGIN_ID(app.getId()) == pluginId)
			.map(app -> app.getId())
			.flatMap(app -> Util.F(null, x -> loadModules(app).stream()))
			.collect(Collectors.toSet());
	}
	
	public Set<Integer> listPlugins() {
		return APPLICATION_LIST.stream().map(app -> PLUGIN_ID(app.getId())).collect(Collectors.toSet());
	}
	
	public X2Module getModuleByClassName(int moduleId,String moduleClassName) {
		return listModules(moduleId).stream()
			.filter(m -> m.getModule().getClass().getName().equals(moduleClassName))
			.findAny().orElse(null);
	}
	
	public CCMessage process(CCMessage msg) throws Throwable {
		final Holder<CCMessage> result = new Holder<>();
		final Holder<Throwable> error = new Holder<>();
		//so first of all this is actually used for both batch processing and standard processing.
		boolean isBatch = msg.getAttributes().containsKey("module_class");
		
		//get the set of valid applications to recieve this message.
		Set<X2ApplicationMetaData> appList = APPLICATION_LIST.stream()
			.filter(app -> app.getId().startsWith(msg.getPluginId()+"_"))
			.filter(app -> isBatch ? app.getBatches().contains(msg.getModuleClass()) : app.getEvents().contains(msg.getModuleClass()))
			.collect(Collectors.toSet());
		
		//we need to find the largest version number amoungst these applications
		double version = 
			appList.stream().mapToDouble(app -> PLUGIN_VERSION(app.getId())).max().orElse(1.0);
		
		Set<X2Module> moduleSet = appList.stream()
			.flatMap(app -> Util.F(null, x -> loadModules(app.getId()).stream()))
			.filter(m -> m.getModule().getClass().getName().equals(msg.getModuleClass()))
			.filter(m -> msg.getPluginVersion() != 0 ? m.getModule().getVersion() == msg.getPluginVersion() : m.getModule().getVersion() == version)
			.collect(Collectors.toSet());

		X2Module module = moduleSet.stream().limit(1).findAny().orElse(null);
		if(module != null) {
			return process(module,(isBatch ? null : msg),null,null); 
		} else {
			throw new RuntimeException(String.format("Module: %d - %s could not be found",msg.getPluginId(), msg.getModuleClass()));
		}
	}
	
	public void process(HttpServletRequest request,HttpServletResponse response) throws Throwable {
		final String fBasePath = request.getServletPath();
		String vModulePath = moduleEndpointMap.keySet().parallelStream().filter((k) -> { return fBasePath.toUpperCase().startsWith(k.toUpperCase()); }).findAny().orElse(null);
		String vBasePath = fBasePath;
		if(vModulePath == null) {
			//lets look for a virtual host mapping.
			String url = request.getRequestURL().toString();
			url = url.substring(url.indexOf(":")+1);
			Pattern p = Pattern.compile("(\\/\\/)([^:]+)(\\:[0-9]+)(\\/.*)");
			Matcher m = p.matcher(url);
			if(m.find()) {
				url = m.group(1)+m.group(2)+m.group(4);
			}
			final String fUrl = url;
			vModulePath = virtualHostMap.entrySet().stream().filter(e -> fUrl.startsWith(e.getKey())).findFirst().orElse(new HashMap.SimpleEntry<>(null,null)).getValue();
			if(vModulePath != null) {
				vBasePath = vModulePath;
			}
		}
		final String modulePath = vModulePath;
		final String basePath = vBasePath;
		if(modulePath != null) {
			String moduleJar = moduleEndpointMap.get(modulePath);
			if(moduleJar != null) {
				//so at this point we need to see if we have loaded this module up yet. we will be able to tell
				//if the module is contained in the module map. but there will be complexity in this as well
				//because if the module is not loaded then we should block until it becomes loaded into the system.
				
				final Holder<Throwable> error = new Holder<>();
				loadModules(moduleJar).parallelStream().filter((m) -> {
					if(m.getModule().getBasePath() != null) {
						if(moduleJar.startsWith("0_")) {
							return basePath.toUpperCase().startsWith(m.getModule().getBasePath().toUpperCase());
						} else {
							return basePath.toUpperCase().startsWith(("/"+PLUGIN_ID(moduleJar)+"/v"+String.valueOf(m.getModule().getVersion())+(m.getModule().getBasePath().startsWith("/") ? "" : "/")+m.getModule().getBasePath()).toUpperCase());
						}
					}
					
					return false;
				}).sorted(new Comparator<X2Module>() {
					@Override
					public int compare(X2Module o1, X2Module o2) {
						return new Integer(o1.getModule().getBasePath().length()).compareTo(o2.getModule().getBasePath().length());
					}
				}).findFirst().ifPresent((m) -> { try { process(m,null,request, response); } catch(Throwable ex) { error.value = ex; } });
				if(error.value != null) {
					response.sendError(500, CCError.newInstance(error.value).toString());
				}
			} else {
				//lets make this error response a bit nicer and return a better formatted and smarter looking list of supported plugins with an outline
				//of the endpoints the classes that implement those endpoints a clickable link to the endpoint and a link to view the module documentation
				//if that is available.
				response.sendError(404, "the path: "+modulePath+" does not currespond to any mappings. supported mappings are: "+moduleEndpointMap.toString());
			}
		} else {
			response.sendError(404, "no module mapping defined for: "+basePath+" supported mappings are: "+moduleEndpointMap.toString());
		}
	}
	
	private static class ModuleExecutionContext {
		private final Module module;
		private final ModuleMetaData metadata;
		private final AbstractX2 server;
		
		public ModuleExecutionContext() {
			this(null,null,null);
		}
		
		public ModuleExecutionContext(Module module,ModuleMetaData metadata,AbstractX2 server) {
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

		public AbstractX2 getServer() {
			return server;
		}
	}
	
	private static final Map<Long,ModuleExecutionContext> MODULE_CONTEXT_MAP = new ConcurrentHashMap<>();
	
	public static Module getCurrentModule() {
		return MODULE_CONTEXT_MAP.getOrDefault(Thread.currentThread().getId(),new ModuleExecutionContext()).getModule();
	}
	
	public static ModuleMetaData getCurrentModuleMetaData() {
		return MODULE_CONTEXT_MAP.getOrDefault(Thread.currentThread().getId(),new ModuleExecutionContext()).getMetadata();
	}
	
	public static AbstractX2 getServerInstance() {
		return MODULE_CONTEXT_MAP.getOrDefault(Thread.currentThread().getId(),new ModuleExecutionContext(null,null,AbstractX2.DEFAULT_INSTANCE == null ? CC.SERVER_INSTANCE : AbstractX2.DEFAULT_INSTANCE)).getServer();
	}
	
	public <T extends Object> T runWithModuleContext(Class<T> retval,ManagedFunctionWithException<X2Module,Object> func) throws Throwable {
		return retval.cast(runWithModule(getModulesByPluginId(0).iterator().next(), x2server.getWORK_EXECUTOR(), func, 600));
	}
	
	private static interface ManagedFunction<T,R> extends ManagedFunctionWithException<T, R> {}
	
	private Object runWithModule(final X2Module m,final ExecutorService exec,ManagedFunctionWithException<X2Module,Object> func,final int timeout) throws Throwable {
		Object retval = null;
		try {
			 retval = buildModuleFuture(m, exec, func).get(timeout, TimeUnit.SECONDS);
		}catch(TimeoutException tmEx) {
			TIMEOUT_COUNT++;
			long freeMemory = MEMORY_CHECK();
			if(freeMemory < MEMORY_THRESHOLD && TIMEOUT_COUNT > 10) {
				x2server.LOG(Level.SEVERE, "Forcing Process Termination because Process Deadlock Detected FreeMem: %d, TimeOutCount: %d", freeMemory, TIMEOUT_COUNT);
				System.exit(0); //kill the process as this means the system is frozen.
			} else {
				x2server.LOG(Level.WARNING, "[%d][%s][%s] Possible Worker Deadlock. Worker reset initiatied FREE MEMORY %d MB TIMEOUT %d", m.getMetaData().getId(), String.valueOf(m.getMetaData().getVersion()), m.getMetaData().getName(), 
					freeMemory / 1024, TIMEOUT_COUNT);
				if(TIMEOUT_COUNT > 10) {
					TIMEOUT_COUNT = 0;
				}
			}
			//timeouts should be re-scheduled.
			retval = tmEx;
		}
		
		if(retval instanceof Throwable) {
			throw (Throwable)retval;
		}
		
		return retval;
	}
	
	private Future<Object> buildModuleFuture(final X2Module m,final ExecutorService exec,ManagedFunctionWithException<X2Module,Object> func) {
		return exec.submit(() -> {
			try {
				MODULE_CONTEXT_MAP.put(Thread.currentThread().getId(), new ModuleExecutionContext(m.getModule(), m.getMetaData(), x2server));
				return func.applyHandleErrors(m);
			}catch(Throwable ex) {
				x2server.LOG(Level.SEVERE, "[%d][%s][%s] Error running module: %s", m.getMetaData().getId(), String.valueOf(m.getMetaData().getVersion()), m.getMetaData().getName(), CCError.toString(ex));
				return ex;
			}finally {
				MODULE_CONTEXT_MAP.remove(Thread.currentThread().getId());
			}
		});
	}
	
	private final ConcurrentHashMap<Integer,Long> BatchExecutionMap = new ConcurrentHashMap<>();
	
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
		return "x2_event_module_"+moduleClass+"_"+instanceId;
	}
	
	private String buildModuleEventStorageKey(final String moduleClass) {
		return buildModuleEventStorageKey(x2server.getINSTANCE_ID(), moduleClass);
	}
	
	private static String buildModuleEventStoragePath(final long moduleId,final double moduleVersion) {
		return CC.SYSTEM_STORAGE_PATH+"/"+String.valueOf(moduleId)+"_"+String.valueOf(moduleVersion);
	}
	
	private static synchronized ModuleInstanceEventInfo getModuleEventRuntimeInfo(final String instanceId,final long moduleId,final double moduleVersion, final String moduleClass) {
		ModuleInstanceEventInfo runtimeInfo = CloudProvider.getInstance().getRuntime().read(ModuleInstanceEventInfo.class,buildModuleEventStoragePath(moduleId, moduleVersion), buildModuleEventStorageKey(instanceId, moduleClass));
		if(runtimeInfo == null) {
			runtimeInfo = new ModuleInstanceEventInfo();
		}
		return runtimeInfo;
	}
	
	private synchronized ModuleInstanceEventInfo getModuleEventRuntimeInfo(final long moduleId,final double moduleVersion, final String moduleClass) {
		return getModuleEventRuntimeInfo(x2server.getINSTANCE_ID(), moduleId, moduleVersion, moduleClass);
	}
	
	public synchronized void writeExecuteModuleEvent(final long moduleId,final double moduleVersion, final String moduleClass) {
		ModuleInstanceEventInfo eventInfo = getModuleEventRuntimeInfo(moduleId, moduleVersion, moduleClass);
		eventInfo.setNumActive(eventInfo.getNumActive()+1);
		eventInfo.getActiveSince().add(System.currentTimeMillis());
		//remove any events over 30 minutes old
		eventInfo.getActiveSince().removeIf(l -> System.currentTimeMillis() - l > TimeUnit.MINUTES.toMillis(30));
		CloudProvider.getInstance().getRuntime().write(buildModuleEventStoragePath(moduleId, moduleVersion), 
			buildModuleEventStorageKey(moduleClass), eventInfo);
	}
	
	public void deleteExecuteModuleEvent(final long moduleId,final double moduleVersion, final String moduleClass) {
		ModuleInstanceEventInfo eventInfo = getModuleEventRuntimeInfo(moduleId, moduleVersion, moduleClass);
		eventInfo.setNumActive(eventInfo.getNumActive()-1);
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
	
	public int getNumModuleEventsRunning(final long moduleId,final double moduleVersion, final String moduleClass) {
		return getNumModuleEventsRunning(x2server.getINSTANCE_ID(), moduleId, moduleVersion, moduleClass);
	}
	
	public long getLastLaunchedModuleEvent(final long moduleId,final double moduleVersion,final String moduleClass) {
		ModuleInstanceEventInfo eventInfo = getModuleEventRuntimeInfo(moduleId, moduleVersion, moduleClass);
		return eventInfo.getActiveSince().stream().max((a1,a2) -> a1.compareTo(a2)).orElse(0l);
	}
	
	public long getLastLaunchedModuleEventOnGSM(final long moduleId,final double moduleVersion,final String moduleClass) {
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
	 * @param moduleId the id of the module to get the number of running instances for
	 * @param moduleVersion the version of the module to get the number of running instances for
	 * @param moduleClass the class implementation of the module to get the number of running instances for.
	 * @return the number of currently active running event processes at this point in time.
	 */
	public static int getNumModuleEventsRunning(final String instanceId, final long moduleId,final double moduleVersion, final String moduleClass) {
		ModuleInstanceEventInfo eventInfo = getModuleEventRuntimeInfo(instanceId, moduleId, moduleVersion, moduleClass);
		return eventInfo.getNumActive();
	}
	
	public int getNumModuleEventsRunningOnLocation(final long moduleId,final double moduleVersion, final String moduleClass) {
		return getNumModuleEventsRunningOnLocation(x2server.getLOCATION(), moduleId, moduleVersion, moduleClass);
	}
	
	public int getNumModuleEventsRunningOnLocation(final String location,final long moduleId,final double moduleVersion, final String moduleClass) {
		return listInstances(location).stream().mapToInt(inst -> getNumModuleEventsRunning(inst, moduleId, moduleVersion, moduleClass)).sum();
	}
	
	public int getNumModuleEventsRunningOnGSM(final long moduleId,final double moduleVersion, final String moduleClass) {
		return getActiveLocationList().stream()
				.flatMap(l -> listInstances(l).stream())
				.mapToInt(inst -> getNumModuleEventsRunning(inst, moduleId, moduleVersion, moduleClass)).sum();
	}
	
	private CCMessage process(final X2Module m,final CCMessage msg,final HttpServletRequest request,final HttpServletResponse response) throws Throwable {
		//we should probably run this in a specific thread
		//where we will inject a context variable which contains information about the plugin that will be running the code in the plugin.
		//the call will block until the module execution completes however we will use a future mechansim to get at the value and return it.
		//the key here will be access to the plugin context always so we can make the execution environment smarter.
		ExecutorService exec = x2server.getWORK_EXECUTOR(); //doesn't really make sense to have a lot of pools because now that this is a cached pool implementation there are no longer any limits.
		int timeoutInSeconds = 60*30; //30 minutes (events can run longer)
		boolean isHttp = false;
		if(request != null) {
			timeoutInSeconds = 20;
			isHttp = true;
		}
		if(msg == null) {
			timeoutInSeconds = (int)TimeUnit.SECONDS.convert(6, TimeUnit.HOURS); //batch tasks can run for up to 6 hours.
		}
		//the code will make sure plugin loading has priority execution.
		if(m.getMetaData().getId() == 0) {
			timeoutInSeconds = 300;
		}
		long start = System.currentTimeMillis();
		try {
			Object retval = runWithModule(m, exec, (t) -> {
			CCMessage msgRet = null;
				if(request == null && response == null) {
					if(msg != null) {
						//write that we are running to the GSM
						try {
							//writeExecuteModuleEvent(m);
							msg.setCurrentInstance(x2server.getINSTANCE_ID());
							msg.setCurrentLocation(x2server.getLOCATION());
							msgRet = m.getModule().process(msg);
						}finally {
							//write that we have stopped running to the GSM.
							//deleteExecuteModuleEvent(m);
						}
					} else {
						/*Long lastExec = BatchExecutionMap.get(m.getMetaData().getId());
						if(lastExec == null || System.currentTimeMillis()-lastExec > TimeUnit.MILLISECONDS.convert(1, TimeUnit.MINUTES)) {
							BatchExecutionMap.put(m.getMetaData().getId(), System.currentTimeMillis());
							try {*/
								m.getModule().processBatch(x2server.getLOCATION(),CC.IS_CLOUD_LOCATION(x2server.getLOCATION()));
							/*}finally {
								BatchExecutionMap.put(m.getMetaData().getId(), System.currentTimeMillis());
							}
						} else {
							x2server.LOG(Level.INFO, "Ignoring execution of batch message because one for this module was run less than a minute ago.");
						}*/
					}
				} else {
					m.getModule().process(request, response);
				}
				return msgRet;
			},timeoutInSeconds);
			if(retval instanceof CCMessage) {
				return (CCMessage)retval;
			}

			return null;
		}catch(Throwable ex) {
			/*if(request == null && response == null && msg != null) {
				deleteExecuteModuleEvent(m);
			}*/
			throw ex;
		}finally {
			long end = System.currentTimeMillis();
			final boolean fIsHttp = isHttp;
			Arrays.asList(SYSTEM_INTERVALS).parallelStream().forEach(interval -> { 
				if(fIsHttp) 
					interval.httpRequest(end-start);
				else
					interval.eventRequest(end-start); 
			});
		}
	}
	
	@Deprecated
	public static synchronized CCPluginManager getInstance() {
		return CC.SERVER_INSTANCE.getPluginManager();
	}
	
	public static Set<String> getClassList(String jarFileName,InputStream dataStream) throws IOException {
		return getClassList(jarFileName, dataStream, false);
	}
	
	public static Set<String> getClassList(String jarFileName,InputStream dataStream,boolean replace) throws IOException {
		List<String> classList = (replace ? null : CloudProvider.getInstance().getRuntime().retrieve(jarFileName+".classlist", List.class));
		if(classList == null) {
			if(dataStream == null) {
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
			CloudProvider.getInstance().getRuntime().store(jarFileName+".classlist", jarClassList);
			classList = jarClassList;
		}
		
		return classList.stream().collect(Collectors.toSet());
	}
	
	public static Set<String> getClassList(InputStream in) throws IOException {
		HashSet<String> classList = new HashSet<>();
		try(JarInputStream jarIs = new JarInputStream(in)) {
			JarEntry jEntry = null;
			while((jEntry = jarIs.getNextJarEntry()) != null) {
				if(jEntry.getName().endsWith(".class")) {
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
		AbstractX2 x2server;
		CCModuleDocumentation documentation;
		CCRuntimeStatistics runtimeStats;
		
		public PluginManagerModule(final AbstractX2 x2server) {
			this.x2server = x2server;
			this.documentation = new CCModuleDocumentation(x2server);
			this.runtimeStats = new CCRuntimeStatistics(x2server);
		}
		
		@Override
		public void construct(Logger logger, String name, int id, double version) {
			this.log = logger;
			this.pluginId = id;
		}

		@Override
		public void start() {}

		@Override
		public void stop() {}
		
		private CCUser authorise(HttpServletRequest request) throws Throwable {
			String authHeader = request.getHeader("Authorization");
			if(authHeader != null) {
				String[] authPart = new String(Base64.getDecoder().decode(authHeader.split(" ")[1]),"UTF-8").split(":");
				String username = authPart[0];
				String password = authPart[1];
				if(username != null && password != null) {
					CCUser user = CCAuthentication.getUser(username);
					if(user != null && user.getPassword().equals(Util.md5(password))) {
						return user;
					}
				}
			}
			
			return null;
		}

		private String buildModuleFileName(String strPluginId,String strPluginName,String strPluginVersion) {
			return strPluginId+"_"+strPluginName+"-"+strPluginVersion+".jar";
		}
		
		@Override
		public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
			//before we allow anyone to proceed we need to check if their username and password is authenticated against the system.
			CCUser authUser = authorise(request);
			if(!("GET".equals(request.getMethod()) /*&& request.getRequestURI().equals("/x2manager/check")*/)) {
				if(authUser == null) {
					response.setStatus(401);
					response.getOutputStream().close();
					return;
				}
			}
			
			/**
			 * forward all authentication or security requests to the security engine.
			 */
			if(request.getRequestURI().startsWith("/x2manager/authentication")) {
				CCAuthentication.processRequest(authUser, request, response);
				return;
			}
			
			/**
			 * forward valid requests to the virtual host manager
			 */
			if(request.getRequestURI().startsWith("/x2manager/virtualhosts")) {
				CCVirtualHostManager.processRequest(authUser, request, response);
				return;
			}
			
			if(request.getRequestURI().startsWith("/x2manager/docs")) {
				documentation.processRequest(authUser, request, response);
				return;
			}
			
			if(request.getRequestURI().startsWith("/x2manager/stats")) {
				runtimeStats.processRequest(authUser, request, response);
				return;
			}
			
			if(request.getRequestURI().startsWith("/x2manager/version")) {
				CCRuntimeVersion.processRequest(authUser, request, response);
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
			if("POST".equals(request.getMethod())) {
				if(request.getContentType().contains("multipart/form-data")) {
					//all plugin update events should be added to an audit log, which will indicate who made the request what plugin it was for and whether it was authorised or not.
					Part pluginData = request.getPart("PLUGIN");
					Part pluginId = request.getPart("ID");
					Part pluginName = request.getPart("NAME");
					Part pluginVersion = request.getPart("VERSION");
					if(pluginData == null || pluginId == null || pluginName == null || pluginVersion == null) {
						response.setStatus(400);
					} else {
						//before we allow an upload we must make sure the user is an administrator or is part of a group which is allowed to deploy to this plugin id.
						//we need to check if the plugin id is in the range this user can access.
						final String strPluginId = CC.toString(pluginId.getInputStream());
						int iPluginId = Integer.parseInt(strPluginId);
						final String strPluginVersion = CC.toString(pluginVersion.getInputStream());
						final String strPluginName = CC.toString(pluginName.getInputStream());
						String pluginFile = buildModuleFileName(strPluginId, strPluginName, strPluginVersion);
						
						if(!authUser.isAdmin()) {
							authUser.extractGroups();
							if(!authUser.getGroups().stream().anyMatch(g -> ((g.getLocations() != null && g.getLocations().contains(x2server.getLOCATION())) 
								|| (g.getLocationPattern() != null && Pattern.matches(g.getLocationPattern(), x2server.getLOCATION())))
								&& (iPluginId >= g.getModule_id_start_range() && iPluginId <= g.getModule_id_end_range())
							)) {
								response.sendError(401, String.format("You do not have permission to deploy plugin %d to the location %s",iPluginId,x2server.getLOCATION()));
								x2server.getLOG_FORMATTER().logEvent(new CloudLogEvent(String.format("[%s][%s][%s][%d][%s][%s] plugin deployment failed because access was denied", CCPluginManager.class.getSimpleName(), x2server.getLOCATION(), authUser.getUsername(), 
									iPluginId,strPluginVersion,pluginFile)).withModuleId(0).withModuleName("X2-AUDIT-LOG"));
								return;
							}
						}
						
						
						//we need to check if the pluginFile is already stored in our bucket, if it is then this is an upgrade candidate if it is a module, we also need to check if the jar is a module
						//a module is defined as a jar which has at least 1 class within it which implements the Module interface.
						
						//we need to get a list of the files in this jar file
						uploadModule(iPluginId,Double.parseDouble(strPluginVersion),strPluginName, pluginData.getInputStream(), authUser);
					}
				} else if(request.getContentType().equalsIgnoreCase("application/json")) {
					//by posting to the plugin manager you can also set plugin focused parameters. These parameters will have to be set by sending the following parameters.
					/**
					 * content-type: should be application/json
					 * ID: the plugin id to apply the parameters too.
					 * 
					 * BODY: the body should contain a JSON document which is built as follows
					 * { "parameters":[
					 *	{
					 *		"key" : "parameter name",
					 *		"value" : "parameter value",
					 *		"operation" : "upsert | delete" default = upsert
					 *  }
					 * ]}
					 * 
					 * parameters will actually be stored in DynamoDB. When parameters are updated through this web service a notification is sent through the 
					 * plugin manager which will call the necessary method on the module which will set through a map containing the new parameters that have been set.
					 */
					 if(request.getParameter("ID") != null && Util.parse(request.getParameter("ID")) != 0) {
						int pluginId = Util.parse(request.getParameter("ID"));
						if(!authUser.isAdmin()) {
							authUser.extractGroups();
							if(!authUser.getGroups().stream().anyMatch(g -> ((g.getLocations() != null && g.getLocations().contains(x2server.getLOCATION())) 
								|| (g.getLocationPattern() != null && Pattern.matches(g.getLocationPattern(), x2server.getLOCATION())))
								&& (pluginId >= g.getModule_id_start_range() && pluginId <= g.getModule_id_end_range())
							)) {
								response.sendError(401, String.format("You do not have permission to change the configuration of the plugin %d using the location %s",pluginId,x2server.getLOCATION()));
								return;
							}
						}
						ModuleConfiguration config = CC.fromJSONString(ModuleConfiguration.class, Util.toString(request.getInputStream()));
						
						if(config != null && !config.getParameters().isEmpty()) {
							CloudProvider.getInstance().getRuntime().setModuleConfiguration(pluginId, config);
							
							//now we also want to propagate all of these parameters to other instances so they may become aware of the changes to the settings for the modules associated
							//to this plugin.
							CCMessage configMsg = new CCMessage();
							configMsg.getAttributes().put("UPDATED_CONFIG", pluginId);
							configMsg.broadcast();
						} else {
							response.setStatus(400); //bad parameters
						}
					} else {
						response.setStatus(400); //bad parameters
					}
				} else {
					response.setStatus(422);
				}
			} else if("GET".equals(request.getMethod())) {
				//if this is the case then we should provide a maven library download facility for our libraries, the get request should start by asking to download a pom.xml file.
				//here there is security in-place so first of all we have to authenticate with a valid CC user and then we will only be able to access libraries or modules which
				//are associated to our credentials
				if(request.getRequestURI().equals("/x2manager/check")) {
					response.setContentType("text/html");
					//here we should check the status of a specific internal service if requested. (only for the http protocol)
					String internalUrl = request.getParameter("ick");
					if(internalUrl == null) {
						long freeMemory = MEMORY_CHECK();
						if(freeMemory < 100000) {
							response.sendError(500,"Dangerously low memory level");
						} else {
							byte[] responseBytes = ("<html><body><h1>OK - FREE MEMORY ["+String.valueOf(freeMemory)+"]<h1></body></html>").getBytes("UTF-8");
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
					//the GET method will also contain 2 jolly libraries for X2, one which will contain the module interface which allows for the development of modules.
					//the second will contain the code for the maven plugin which allows new modules to be uploaded to X2.
					if(request.getRequestURI().endsWith(".pom")) {
						response.setContentType("text/xml");
						//this means we are looking for a maven file which describes a library which we would like to use in our
						//project. we can upload libraries to X2 and consume and include them in our projects this way.
						try(OutputStream out = response.getOutputStream()) {
							out.write(buildPOM(request.getRequestURI()));
							out.flush();
						}
					} else if(request.getRequestURI().endsWith(".pom.sha1")) {
						response.setContentType("text/plain");

						try(OutputStream out = response.getOutputStream()) {
							out.write(DigestUtils.sha1Hex(buildPOM(request.getRequestURI().substring(0, request.getRequestURI().lastIndexOf('.')))).getBytes("UTF-8"));
							out.flush();
						}
					} else if(request.getRequestURI().endsWith(".pom.md5")) {
						response.setContentType("text/plain");

						try(OutputStream out = response.getOutputStream()) {
							out.write(DigestUtils.md5Hex(buildPOM(request.getRequestURI().substring(0, request.getRequestURI().lastIndexOf('.')))).getBytes("UTF-8"));
							out.flush();
						}
					} else if(request.getRequestURI().endsWith(".jar")) {
						response.setContentType("application/jar");

						byte[] jarBytes = buildJAR(request.getRequestURI());
						if(jarBytes != null) {
							try(OutputStream out = response.getOutputStream()) {
								out.write(jarBytes);
								out.flush();
							}
						} else {
							response.sendError(404);
						}
					} else if(request.getRequestURI().endsWith(".jar.sha1")) {
						response.setContentType("text/plain");

						try(OutputStream out = response.getOutputStream()) {
							out.write(DigestUtils.sha1Hex(buildJAR(request.getRequestURI().substring(0, request.getRequestURI().lastIndexOf('.')))).getBytes("UTF-8"));
							out.flush();
						}
					} else if(request.getRequestURI().endsWith(".jar.md5")) {
						response.setContentType("text/plain");

						try(OutputStream out = response.getOutputStream()) {
							out.write(DigestUtils.md5Hex(buildJAR(request.getRequestURI().substring(0, request.getRequestURI().lastIndexOf('.')))).getBytes("UTF-8"));
							out.flush();
						}
					} else {
						log.log(Level.INFO,"[MAVEN ADAPTER] - I was asked to download: %s",request.getRequestURI());
						response.sendError(404);
					}
				}
			} else {
				response.sendError(405);
			}
		}
		
		public void uploadModule(int moduleId,double moduleVersion,String moduleName,InputStream moduleDataStream) throws Throwable {
			uploadModule(moduleId, moduleVersion, moduleName, moduleDataStream, null);
		}
		
		public void uploadModule(int moduleId,double moduleVersion,String moduleName,InputStream moduleDataStream,CCUser authUser) throws Throwable {
			final String pluginFile = buildModuleFileName(String.valueOf(moduleId), moduleName, String.valueOf(moduleVersion));
			File tmpUploadFile = File.createTempFile(pluginFile, UUID.randomUUID().toString()+".upload");
			try {
				if(authUser == null) {
					//we should retrieve the first administrator from our repository of users.
					authUser = CCAuthentication.getDefaultAdminUser();
				}
				CC.stream(new FileOutputStream(tmpUploadFile), moduleDataStream, true);

				Set<String> classList = getClassList(pluginFile, new FileInputStream(tmpUploadFile), true);
				if(!classList.isEmpty()) {
					CloudProvider.getInstance().getRuntime().uploadModule(pluginFile, new FileInputStream(tmpUploadFile), tmpUploadFile.length());

					//we now need run any events that are registered.
					List<String> deadEventListeners = new ArrayList<>();
					List<String> validQueueIds = CloudProvider.getInstance().getRuntime().listQueueIds(null);
					List<ModuleEventListener> uploadModuleListeners = x2server.getPluginManager().eventListeners.getOrDefault(EVENT_MODULE_UPLOAD,new ArrayList<>());
					uploadModuleListeners.stream().distinct().forEach((el) -> {
						//check if the location actually exists before sending the message.
						if(validQueueIds.contains(el.getLocation())) {
							CCMessage msg = new CCMessage();
							msg.setPluginId(el.getPluginId());
							msg.setModuleClass(el.getModuleClass());
							msg.getAttributes().put("jarFile", pluginFile);
							msg.getAttributes().put("pluginId", String.valueOf(moduleId));
							msg.getAttributes().put("pluginVersion", String.valueOf(moduleVersion));
							msg.getAttributes().put("pluginName", moduleName);
							msg.getAttributes().put("classList", classList);
							msg.getAttributes().put("url", "http://localhost:8081/x2manager/com/cloudreach/connect/x2/"+moduleName+"/"+String.valueOf(moduleVersion)+"/"+String.valueOf(moduleId)+"_"+moduleName+"-"+String.valueOf(moduleVersion)+".jar");
							msg.send(el.getLocation()); //send back the result of the request.
						} else {
							deadEventListeners.add(el.getLocation());
						}
					});
					uploadModuleListeners.removeIf(el -> deadEventListeners.contains(el.getLocation()));
					synchronized(x2server.getPluginManager().eventListeners) {
						uploadModuleListeners = x2server.getPluginManager().eventListeners.getOrDefault(EVENT_MODULE_UPLOAD,new ArrayList<>());
						x2server.getPluginManager().eventListeners.put(EVENT_MODULE_UPLOAD,uploadModuleListeners.stream().distinct().collect(Collectors.toList()));
					}
					deadEventListeners.clear();
					validQueueIds.clear();
				}

				try(X2ClassLoader x2loader = new X2ClassLoader(UUID.randomUUID().toString(), new FileInputStream(tmpUploadFile), X2ClassLoader.class.getClassLoader())) {
					//on upload it will make sense to save the list of x2 modules somewhere for future use.
					//we need to update the crc of the new file here.
					CloudProvider.getInstance().getRuntime().store(pluginFile+".crc32", x2loader.getCRC32());
					List<String> x2ModuleList = x2server.getPluginManager().MODULE_LIST(classList, x2loader);
					if(!x2ModuleList.isEmpty()) {
						//so the first thing we should be doing here is producing the new metadata for this application and saving it to the cloud
						X2ApplicationMetaData app = x2server.getPluginManager().registerModule(pluginFile, authUser.getUsername(), x2loader, x2ModuleList);
						SystemDB.save(MODULE_METADATA_TABLE, app); //save this metadata
						
						//this is pointless
						//x2server.getPluginManager().storeModuleList(); //store the module list so we know what modules are available in this app for the cluster.
						
						log.log(Level.INFO, "The Jar %s contains X2 modules, notify other instances about it so they can process it''s presence accordingly.", pluginFile);
						//lets save the module list to our module storage unit
						CloudProvider.getInstance().getRuntime().storeModuleList(pluginFile, x2ModuleList);

						x2server.getLOG_FORMATTER().logEvent(new CloudLogEvent(String.format("[%s][%s][%s][%d][%s][%s] module deployment succeeded. The following module implementations were deployed %s", 
							CCPluginManager.class.getSimpleName(), x2server.getLOCATION(), authUser.getUsername(), 
							moduleId,String.valueOf(moduleVersion),pluginFile, x2ModuleList)).withModuleId(0).withModuleName("X2-AUDIT-LOG"));

						//we will send this message for compatibility with X2 2.2 and lower.
						CCMessage msg = new CCMessage();
						msg.setModuleClass(getClass().getName());
						msg.setPluginId(this.pluginId);
						msg.setSourceInstance(x2server.getINSTANCE_QUEUE_URL());
						msg.setSourcePluginId(this.pluginId);
						msg.getAttributes().put("file", pluginFile);
						msg.getAttributes().put("ignore","true");
						msg.broadcast();

						//however we should now send a different message announcing that this module has changed
						msg.getAttributes().clear();
						msg.getAttributes().put("application_metadata",Util.toJSONString(app));
						msg.broadcast();

						x2server.getMODULE_BATCH_BLACKLIST().removeIf(m -> m.startsWith(String.valueOf(moduleId)+":"+String.valueOf(moduleVersion)));
					} else {
						x2server.getLOG_FORMATTER().logEvent(new CloudLogEvent(String.format("[%s][%s][%s][%d][%s][%s] library installation succeeded.", CCPluginManager.class.getSimpleName(), x2server.getLOCATION(), authUser.getUsername(), 
							moduleId,String.valueOf(moduleVersion),pluginFile)).withModuleId(0).withModuleName("X2-AUDIT-LOG"));

						CCMessage msg = new CCMessage();
						msg.setModuleClass(getClass().getName());
						msg.setPluginId(this.pluginId);
						msg.setSourceInstance(x2server.getINSTANCE_QUEUE_URL());
						msg.setSourcePluginId(this.pluginId);
						msg.getAttributes().put("library", pluginFile);
						msg.broadcast();
						log.log(Level.INFO, "The Jar %s does not contain any classes which implement an X2 module it will be stored as a library", pluginFile);
					}
				}finally {
					System.gc(); //force garbage collection to unload unused classes.
				}
			} finally {
				tmpUploadFile.delete();
			}
		}
		
		protected byte[] buildJAR(String requestURI) throws Throwable {
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			String modSpecName = requestURI.substring(requestURI.lastIndexOf('/')+1,requestURI.lastIndexOf('.'));
			String modVersion = modSpecName.substring(modSpecName.lastIndexOf('-')+1);
			if(requestURI.equals("/x2manager/com/cloudreach/connect/x2/module-api/"+modVersion+"/module-api-"+modVersion+".jar")) {
				writeAPIJar(byteOut);
				
				return byteOut.toByteArray();
			} else {
				String modName = modSpecName.substring(0,modSpecName.lastIndexOf('-'));
				
				Set<String> moduleList = CloudProvider.getInstance().getRuntime().listModules();
				String moduleJar = moduleList.stream()
								.filter((so) -> { return (so.startsWith(modName) && so.endsWith(modVersion+".jar")); }).findFirst().orElse(null);
				if(moduleJar != null) {
					return CloudProvider.getInstance().getRuntime().getModule(moduleJar).getData();
				}
			}
			
			return null;
		}
		
		protected byte[] buildPOM(String requestURI) throws UnsupportedEncodingException {
			String modSpecName = requestURI.substring(requestURI.lastIndexOf('/')+1,requestURI.lastIndexOf('.'));
			String modVersion = modSpecName.substring(modSpecName.lastIndexOf('-')+1);
			if(requestURI.equals("/x2manager/com/cloudreach/connect/x2/module-api/"+modVersion+"/module-api-"+modVersion+".pom")) {
				return getPOM(modVersion).getBytes("UTF-8");
			} else {
				//we should check we have a module name loaded on S3 which looks like the end part of this url.
				String modName = modSpecName.substring(0,modSpecName.lastIndexOf('-'));
				String groupId = requestURI.substring("/x2manager/".length(), requestURI.lastIndexOf("/"+modName+"/")).replace('/', '.');
				log.log(Level.FINE,"[MAVEN ADAPTER] - MODULE: %s VERSION %s GROUP_ID %s", new Object[] { modName, modVersion, groupId });
				return getPOM(modName, modVersion, groupId).getBytes("UTF-8");
			}
		}
		
		private void updateApplicationRuntimeVariables(final String jarFileName,final X2ApplicationMetaData app) {
			synchronized(x2server.getPluginManager().KNOWN_APPLICATIONS) {
				x2server.getPluginManager().KNOWN_APPLICATIONS.removeIf(a -> a.getId().equals(jarFileName));
				x2server.getPluginManager().KNOWN_APPLICATIONS.add(app);
			}
			if(x2server.getPluginManager().PLUGIN_VALID(jarFileName)) { //only change the registration in the application list if we are authorised to run this application
				synchronized(x2server.getPluginManager().APPLICATION_LIST) {
					x2server.getPluginManager().APPLICATION_LIST.removeIf(a -> a.getId().equals(jarFileName));
					x2server.getPluginManager().APPLICATION_LIST.add(app);
				}
				Set<String> deadEndpointMappings = x2server.getPluginManager().moduleEndpointMap.entrySet().stream().filter(e -> app.getId().equals(e.getValue())).map(e -> e.getKey()).collect(Collectors.toSet());
				deadEndpointMappings.forEach(k -> x2server.getPluginManager().moduleEndpointMap.remove(k));
				x2server.getPluginManager().moduleEndpointMap.putAll(app.getEndpoints().values().stream()
					.collect(Collectors.toMap(e -> e, e -> app.getId())));
			}
			x2server.getPluginManager().storeModuleList(); //update the module list to reflect the validity of available applications for this instance.
		} 

		@Override
		public CCMessage process(CCMessage message) throws Throwable {
			//cc instances will respond to publish messages for modules as they will trigger the class loading scheme within the plugin manager,
			//please note that publish messages will only be sent for modules.
			if(message.getAttributes().containsKey("file") && !message.getAttributes().containsKey("ignore")) { //we will be expecting this from X2 2.2 or lower instances
				//1. we need to attempt to unload the jar because it may have already been loaded and if it has
				//then we should unload it so the next time it is actually used the new version will be loaded
				String jarFileName = (String)message.getAttributes().get("file");
				x2server.getPluginManager().unloadModule(jarFileName);
				documentation.unloadModule(jarFileName);
				
				//next we are going to build and save the metadata because if it is comming from an old version
				//then older versions will not do this for us.
				
				try(X2ClassLoader x2loader = buildModuleClassLoader(jarFileName)) {
					List<String> newModuleList = x2server.getPluginManager().MODULE_LIST(jarFileName);
					X2ApplicationMetaData app = x2server.getPluginManager().registerModule(jarFileName, "Legacy X2 version", x2loader, newModuleList);
					SystemDB.save(MODULE_METADATA_TABLE, app); //save this metadata
					
					updateApplicationRuntimeVariables(jarFileName, app);
				}
			} else if(message.getAttributes().containsKey("application_metadata")) {
				X2ApplicationMetaData app = Util.fromJSONString(X2ApplicationMetaData.class, (String)message.getAttributes().get("application_metadata"));
				if(app != null) {
					x2server.getPluginManager().unloadModule(app.getId());
					documentation.unloadModule(app.getId());
					updateApplicationRuntimeVariables(app.getId(), app);
				}
			} else if(message.getAttributes().containsKey("library")) {
				documentation.unloadModule((String)message.getAttributes().get("library"));
			} else if(message.getAttributes().containsKey("UPDATED_CONFIG")) {
				//we need to grab the configuration from dynamodb for this plugin.
				int targetPluginId = (Integer)message.getAttributes().get("UPDATED_CONFIG");
				final Map<String,String> config = x2server.getPluginManager().getModuleConfiguration(targetPluginId);
				x2server.getPluginManager().listModules(targetPluginId)
											 .parallelStream().forEach((m) -> { m.getModule().configure(config); });
			} else if(message.getAttributes().containsKey("METADATA") && message.getSourceModuleClass() != null) {
				//a metadata request message is a message from another module which would like a list of the jars that we have loaded and what
				//are the names of the classes in those jars.
				CloudProvider.getInstance().getRuntime().listModules().parallelStream().forEach((moduleJar) -> {
					if(moduleJar.endsWith(".jar")) {
						try {
							String moduleSpec = moduleJar.substring(0,moduleJar.lastIndexOf('.'));
							String pluginId = moduleSpec.substring(0, moduleSpec.indexOf('_'));
							String pluginVersion = moduleSpec.substring(moduleSpec.lastIndexOf('-')+1);
							String pluginName = moduleSpec.substring(moduleSpec.indexOf('_')+1,moduleSpec.lastIndexOf('-'));
							Set<String> classList = getClassList(moduleJar, null, false);
							CCMessage msg = new CCMessage();
							msg.setPluginId(message.getSourcePluginId());
							msg.setModuleClass(message.getSourceModuleClass());
							msg.getAttributes().put("jarFile", moduleJar);
							msg.getAttributes().put("pluginId", pluginId);
							msg.getAttributes().put("pluginVersion", pluginVersion);
							msg.getAttributes().put("pluginName", pluginName);
							msg.getAttributes().put("classList", classList);
							msg.getAttributes().put("url", "http://localhost:8081/x2manager/com/cloudreach/connect/x2/"+pluginName+"/"+pluginVersion+"/"+pluginId+"_"+pluginName+"-"+pluginVersion+".jar");
							msg.send(message.getSourceInstance()); //send back the result of the request.
						}catch(IOException ioEx) {}
					}
				});
			} else if(message.getAttributes().containsKey("EVENT") && message.getSourceModuleClass() != null) {
				List<ModuleEventListener> eventListeners = x2server.getPluginManager().eventListeners.get((String)message.getAttributes().get("EVENT"));
				if(eventListeners == null) {
					eventListeners = new CopyOnWriteArrayList<>();
					x2server.getPluginManager().eventListeners.put((String)message.getAttributes().get("EVENT"),eventListeners);
				}
				if(!eventListeners.stream().anyMatch(el -> el.getPluginId() == message.getSourcePluginId() 
								&& el.getModuleClass().equalsIgnoreCase(message.getSourceModuleClass())
								&& el.getLocation().equalsIgnoreCase(message.getSourceInstance()))) {
					eventListeners.add(new ModuleEventListener(message.getSourcePluginId(), message.getSourceModuleClass(), message.getSourceInstance()));
				}
			} else if(message.getAttributes().containsKey(CCVirtualHostManager.EVENT_RELOAD)) {
				x2server.getPluginManager().loadVirtualHostDefinitions();
			} else if(message.getAttributes().containsKey("START_FIXED_APP") && message.getAttributes().containsKey("START_FIXED_MODULE")) {
				X2ApplicationMetaData metadata = Util.fromJSONString(X2ApplicationMetaData.class, message.getAttributes().get("START_FIXED_APP").toString());
				final String module = message.getAttributes().get("START_FIXED_MODULE").toString();
				
				Set<X2Module> moduleList = null;
				do {
					moduleList = x2server.getPluginManager().loadModules(metadata.getId());
					x2server.LOG(Level.INFO, "[%s] FIXED PROCESS: We asked for the list of modules and were given the following: %s",metadata.getId(), moduleList == null ? "null" : moduleList.stream().map(m -> m.getModule().getClass().getName()).collect(Collectors.joining(",")));
					if(moduleList == null) {
						Thread.sleep(250); //wait a quarter of a second before retrying to module load.
					}
				}while(moduleList == null);
				x2server.LOG(Level.INFO,"[%s][%s] - A request was made to run the fixed method of the module. The following modules were loaded in the application [%s]", metadata.getId(), module,
					moduleList.stream().map(m -> m.getModule().getClass().getName()).collect(Collectors.joining(",")));
				//we have a null pointer exception somewhere in this statement.
				moduleList.stream()
					.filter(m -> m.getModule().getClass().getName().equals(module))
					.findAny().ifPresent(m -> x2server.getPluginManager().startFixedProcess(m,metadata.getId()));
			}
			
			return null;
		}

		@Override
		public String getBasePath() {
			return "/x2manager";
		}

		@Override
		public double getVersion() {
			return 1.0;
		}
		
		private String getPOM(String module,String version,String groupId) {
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
			pom.append("<groupId>com.cloudreach.connect.x2</groupId>");
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
			Util.createJar(out, Module.class, CCMessage.class, CCError.class, CloudRuntime.class,
				CloudBlob.class, QueueDoesNotExistException.class, CloudLogEvent.class, CloudQueueProcessor.class,
				SystemDBObject.class, ModuleConfiguration.class, X2ClassLoader.class, CloudProvider.class,
				SystemDB.class, Util.class, CCUser.class, CCGroup.class, CCAuthentication.class,
				ManagedFunctionWithException.class, ManagedAcceptor.class, ManagedConsumer.class,
				Frequency.class, ModuleLimit.class, KeyValue.class);
		}

		@Override
		public void processBatch(String location, boolean isCloudLocation) throws Throwable {
			//we want to make sure this batch is executed only once across the entire GSM.
			//to do this we will write a lock file onto our cloud storage solution which contains the date in which we started the process
			//and before we do this we will read the same file to see if some one else has the lock.
			final CloudRuntime rt = CloudProvider.getInstance().getRuntime();
			Long lockTime = rt.read(Long.class, CC.SYSTEM_STORAGE_PATH, CC.LOCK_FILE_FIXED_PROCESS);
			if(lockTime == null || System.currentTimeMillis() - lockTime > TimeUnit.MINUTES.toMillis(3)) { //or the lock file does not exist or it is stale (older than 3 minutes).
				rt.write(CC.SYSTEM_STORAGE_PATH, CC.LOCK_FILE_FIXED_PROCESS, System.currentTimeMillis()); //write the lock file to avoid other processes starting up.
				
				try {
					//1. we need to get a comprehensive list of the modules which implement a fixed process.
					List<X2ApplicationMetaData> appList = SystemDB.list(MODULE_METADATA_TABLE, X2ApplicationMetaData.class)
						.stream().filter(app -> !app.getFixed().isEmpty()).collect(Collectors.toList());

					//1.5 we need to get a list of all active locations
					Set<String> locationList = x2server.getPluginManager().getActiveLocationList();
					
					//2. we need a list of active instances in this location.
					Map<String,String> instanceLocationMap = locationList.stream()
						.flatMap(l -> x2server.getPluginManager().listInstances(l).stream()
							.map(i -> new AbstractMap.SimpleEntry<>(i,l))
						)
						.collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));
					Set<String> instanceList = instanceLocationMap.keySet();

					//3. of these applications we need to find out which ones are active. Keep in mind that an active application
					//will save it's state to S3 at least every minute.

					List<X2FixedModuleState> allModuleState = appList.stream() //the module state is the list of effectively active and running fixed modules for this location.
						.flatMap(app -> instanceList.stream()
							.flatMap(inst -> app.getFixed().stream().map(mod -> new AbstractMap.SimpleEntry<>(inst,mod)))
							.map(mod -> new X2FixedModuleState(app, mod.getValue(), rt.read(Long.class, CC.SYSTEM_STORAGE_PATH, "lastactive-"+mod.getValue()+"-"+mod.getKey()+"-"+String.valueOf(app.getId())), mod.getKey()))
						)
						.collect(Collectors.toList());

					List<X2FixedModuleState> activeModuleState = allModuleState.stream()
						.filter(st -> st.getLastActiveOn() != null && System.currentTimeMillis() - st.getLastActiveOn() < TimeUnit.MINUTES.toMillis(5)) //anything which has not told us it is active within the last 5 minutes is dead
						.collect(Collectors.toList());
					
					//4. we need to build a list which contains the modules which are in a state which is in-compatible with their limits.
					//these are modules that are either not within the module state list or are in the state list but there are not enough processes running based on the limits.
					
					//1. we need to know the target number of processes on a per app/module basis and the limits associated with them.
					List<X2FixedModuleState> fixedProcessesToStart = allModuleState.stream()
						.flatMap(ms -> {
							List<X2FixedModuleState> appModuleState = activeModuleState.stream()
								.filter(am -> am.getApplication().getId().equals(ms.getApplication().getId())
									&& am.getModule().equals(ms.getModule()))
								.collect(Collectors.toList());
							
							//this means that this is not active anywhere and that means that we should generate as many state entries as processes which need to be activated
							//of course with the right coordinates.
							ModuleLimit limit = ms.getApplication().getLimits().get(ms.getModule());
							//we need to know the total number of instances of this process we need. (a value of -1 means ignore)
							Set<String> validLocationList = locationList.stream().filter(l -> limit.getFixedLocations() == null || Arrays.asList(limit.getFixedLocations()).contains(l)).collect(Collectors.toSet());
							
							List<X2FixedModuleState> newModuleStateList = new ArrayList<>();
							if(!validLocationList.isEmpty()) {
								int totalInstances = limit.getMaxActiveFixedPerGSM() != -1 ? limit.getMaxActiveFixedPerGSM() :
									(limit.getMaxActiveFixedPerLocation() != -1 ? limit.getMaxActiveFixedPerLocation()*validLocationList.size() :
									(limit.getMaxActiveFixedPerInstance() != -1 ? limit.getMaxActiveFixedPerInstance()*instanceList.size() : instanceList.size()));

								totalInstances -= appModuleState.stream().map(st -> st.getInstance()).count();
								x2server.LOG(Level.INFO,"[%s] we are missing %d instances in the GSM the size of the instance list is %d the number of active instances is %d", ms.getModule(), totalInstances, instanceList.size(),
									appModuleState.stream().map(st -> st.getInstance()).count());
								IntStream.range(0, totalInstances)
									.boxed()
									.forEach(i -> {
										//1. get the location with the least number of instances assigned.
										Map<String,List<X2FixedModuleState>> stateByLocation = Stream.concat(newModuleStateList.stream(), appModuleState.stream())
											.collect(Collectors.groupingBy(st -> instanceLocationMap.get(st.getInstance())));

										stateByLocation.putAll(validLocationList.stream().filter(l -> !stateByLocation.containsKey(l)).collect(Collectors.toMap(l -> l, l -> new ArrayList<>())));

										final String targetLocation = stateByLocation.entrySet()
											.stream()
											.min((e1,e2) -> new Integer(e1.getValue().size()).compareTo(e2.getValue().size()))
											.map(e -> e.getKey())
											.orElse(location);

										Map<String,List<X2FixedModuleState>> stateByInstance = Stream.concat(newModuleStateList.stream(), appModuleState.stream())
											.collect(Collectors.groupingBy(st -> st.getInstance()));

										Set<String> validInstanceList = instanceLocationMap.entrySet().stream()
											.filter(e -> e.getValue().equals(targetLocation))
											.map(e -> e.getKey())
											.collect(Collectors.toSet());

										stateByInstance.putAll(validInstanceList.stream().filter(inst -> !stateByInstance.containsKey(inst)).collect(Collectors.toMap(inst -> inst, inst -> new ArrayList<>())));

										//instance distribution makes sense but only if we are not targeting a fixed number of processes per instance.
										String targetInstance = stateByInstance.entrySet()
											.stream()
											.min((e1,e2) -> new Integer(e1.getValue().size()).compareTo(e2.getValue().size()))
											.map(e -> e.getKey())
											.orElse(x2server.getINSTANCE_ID());

										//once we have a target instance we need to check how many modules we have active in this instance.
										final String fTargetInstance = targetInstance;
										final long numActiveInTargetInstance = Stream.concat(newModuleStateList.stream(),appModuleState.stream()).filter(app -> app.getInstance().equals(fTargetInstance)).count();
										if(limit.getMaxActiveFixedPerInstance() != -1 && numActiveInTargetInstance >= limit.getMaxActiveFixedPerInstance()) {
											//this will make this instance invalid, so we should search for an instance which is under the limit
											targetInstance = instanceLocationMap.entrySet().stream()
												.filter(e -> validLocationList.contains(e.getValue()))
												.filter(e -> Stream.concat(newModuleStateList.stream(),appModuleState.stream()).filter(app -> app.getInstance().equals(e.getKey())).count() < limit.getMaxActiveFixedPerInstance())
												.map(e -> e.getKey())
												.findAny().orElse(null);
										}
										x2server.LOG(Level.INFO,"[%s][%s] GSM Process Distribution %s selected target instance %s",ms.getApplication().getId(), ms.getModule(),
											instanceLocationMap.entrySet().stream()
												.map(e -> String.format("LOCATION: %s INSTANCE:[%s] TOTAL: %d", e.getValue(), e.getKey(), Stream.concat(newModuleStateList.stream(),appModuleState.stream()).filter(app -> app.getInstance().equals(e.getKey())).count()))
												.collect(Collectors.joining(" - ")), targetInstance
										);
										if(targetInstance != null) {
											X2FixedModuleState newModuleState = new X2FixedModuleState(ms.getApplication(), ms.getModule(), null, targetInstance);
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
					for(X2FixedModuleState st : fixedProcessesToStart) {
						CCMessage msg = new CCMessage();
						msg.setPluginId(0); //send to myself.
						msg.setModuleClass(getClass().getName()); //target myself as a module
						msg.setPluginVersion(1.0); //set to myself
						msg.getAttributes().put("START_FIXED_APP", Util.toJSONString(st.getApplication()));
						msg.getAttributes().put("START_FIXED_MODULE", st.getModule());
						msg.send(CloudProvider.getInstance().getRuntime().getQueueId("CC-"+instanceLocationMap.get(st.getInstance())+"-"+st.getInstance()));
					}
					x2server.LOG(Level.INFO,"Total GSM Instances %d Started Processes %d Fixed Processes as follows: %s",instanceList.size(),fixedProcessesToStart.size(), fixedProcessesToStart.stream()
						.map(proc -> proc.getApplication().getId()+":"+proc.getModule()+":"+proc.getInstance()).collect(Collectors.joining(",")));
				}finally {
					//remove the lock file. (we should delay this as we definately want to keep the lock for at least 1 minute)
					rt.remove(CC.SYSTEM_STORAGE_PATH, CC.LOCK_FILE_FIXED_PROCESS);
				}
			} else {
				x2server.LOG(Level.INFO,"Batch process request ignored because lock file is present");
			}
		}

		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveBatchesPerGSM(1)
				.build();
		}

	}
	
	public Map<String,String> getModuleConfiguration(int pluginId) {
		return CloudProvider.getInstance().getRuntime().getModuleConfiguration(pluginId);
	}
	
	public static X2ClassLoader buildModuleClassLoader(String moduleJar) throws IOException {
		//documentation for something which is not a module is being requested, this means we will likely have to download from our code storage
		Long moduleCRC32 = CloudProvider.getInstance().getRuntime().retrieve(moduleJar+".crc32", Long.class);
		X2ClassLoader modClassLoader = null;
		if(moduleCRC32 == null || moduleCRC32 == 0) {
			CloudBlob modBlob = CloudProvider.getInstance().getRuntime().getModule(moduleJar);
			if(modBlob != null) {
				try(InputStream data = modBlob.getDataStream()) {
					modClassLoader = new X2ClassLoader(UUID.randomUUID().toString(), data, CC.class.getClassLoader());
				}
			}
		} else {
			modClassLoader = new X2ClassLoader(UUID.randomUUID().toString(), moduleJar, moduleCRC32 == null ? 0 : moduleCRC32, CC.class.getClassLoader());
		}
		if(moduleCRC32 == null) {
			CloudProvider.getInstance().getRuntime().store(moduleJar+".crc32", modClassLoader.getCRC32());
		}
		return modClassLoader;
	}
	
	/**
	 * this method will unload a module defined by the jar file name passed.
	 * @param jarFileName the name of the jar file to unload.
	 */
	public void unloadModule(String jarFileName) throws IOException {
		if(LIVE_MODULE_MAP.containsKey(jarFileName)) {
			long start = System.currentTimeMillis();
			Set<X2Module> moduleSet = LIVE_MODULE_MAP.get(jarFileName);
			Holder<X2ClassLoader> moduleClassLoader = new Holder<>();
			moduleSet.stream().forEach((originalModule) -> {
				try {
					if(moduleClassLoader.value == null) {
						moduleClassLoader.value = (X2ClassLoader)originalModule.getModule().getClass().getClassLoader();
					}
					runWithModule(originalModule, x2server.getWORK_EXECUTOR(), (m) -> {
							m.getModule().stop(); 
							return null;
					},10);
					originalModule.close(); //shutdown any watchdogs
				}catch(Throwable ex) {}
			});
			if(moduleClassLoader.value != null) {
				moduleClassLoader.value.close();
			}
			LIVE_MODULE_MAP.get(jarFileName).clear();
			x2server.LOG(Level.INFO, "[%s][%s] was unloaded from the system successfully. The process took %d (ms)", getClass().getSimpleName(), jarFileName, System.currentTimeMillis() - start);
		}
	}
	
	/**
	 * this method will register a module with the system, this essentially means writing the fact that
	 * it exists to our NoSQL data store.
	 * 
	 * @returns true if the module already exists in the system and it's metadata was updated
	 * or false if the module did not exist in the system and it was installed as net new.
	 */
	private X2ApplicationMetaData registerModule(final String jarFileName,final String username,final X2ClassLoader appClassLoader,final List<String> moduleList) 
		throws IOException,ClassNotFoundException,InstantiationException,IllegalAccessException {
		//1. see if we can find an existing registration for this module in the system.
		X2ApplicationMetaData appMetadata = CloudProvider.getInstance().getRuntime()
			.getNoSQL(MODULE_METADATA_TABLE, jarFileName, X2ApplicationMetaData.class);
		
		final boolean appExists = appMetadata != null;
		if(appMetadata == null) {
			appMetadata = new X2ApplicationMetaData();
			appMetadata.setId(jarFileName);
			appMetadata.setInstallDate(System.currentTimeMillis());
			appMetadata.setName(PLUGIN_NAME(jarFileName));
			appMetadata.setVersion(PLUGIN_VERSION(jarFileName));
		} else {
			appMetadata.setLastUpgradeDate(System.currentTimeMillis());
		}
		appMetadata.setLastUploadedBy(username);
		
		//we now need to discover the details of the modules.
		for(String moduleClass : moduleList) {
			Class cls = appClassLoader.loadClass(moduleClass);
			Module mod = Module.class.cast(cls.newInstance());
			appMetadata.getLimits().put(moduleClass, X2ApplicationMetaData.X2ModuleLimits.wrap(mod.getLimits()));
			if(X2Module.implementsBatch(cls)) {
				appMetadata.getBatches().add(moduleClass);
			}
			if(X2Module.implementsEvent(cls)) {
				appMetadata.getEvents().add(moduleClass);
			}
			if(X2Module.implementsWeb(cls)) {
				appMetadata.getEndpoints().put(moduleClass,
					"/"+PLUGIN_ID(jarFileName)+"/v"+String.valueOf(PLUGIN_VERSION(jarFileName))+(mod.getBasePath().startsWith("/") ? mod.getBasePath() : "/"+mod.getBasePath()));
			}
			if(X2Module.implementsFixed(cls)) {
				appMetadata.getFixed().add(moduleClass);
			}
		}
		
		return appMetadata;
	}
	
	/**
	 * we generally need to re-think how this is done because it really only makes sense to load
	 * the modules physically into memory when they are actually needed and if they have not been used for
	 * a period of time they should also be unloaded automatically.
	 * 
	 * we should also split what is done here into two different tasks.
	 * 1. registerModule - makes the overall system aware of the modules metadata (these are the actual modules which have been defined)
	 * 2. installModule - this will take a new jar file an deploy it onto the system.
	 * 
	 * the changes above will make the overall system more lightweight and will decrease startup times.
	 * 
	 * @param jarFileName the name of the jar module to load into the system.
	 * @throws IOException if there was a problem loading or registering the module.
	 */
	private void loadModule(String jarFileName) throws IOException {
		if(PLUGIN_VALID(jarFileName)) {
			try {
				//ok now all that we need to do at this point is load the jar into the JCL and instantiate any modules that
				//are contained within it. We will also shutdown any existing modules if they have already been associated with the plugin id specified.
				List<String> newModuleList = x2server.getPluginManager().MODULE_LIST(jarFileName);
				Holder<X2ClassLoader> x2cl = new Holder<>();
				Holder<Long> uploadDate = new Holder<>();
				Holder<Long> installDate = new Holder<>();
				if(!newModuleList.isEmpty()) {
					x2cl.value = buildModuleClassLoader(jarFileName);
					uploadDate.value = x2cl.value.getCreatedDate();
					installDate.value = CloudProvider.getInstance().getRuntime().getModuleInstallDate(jarFileName);
					PLUGIN_MANAGER_MODULE.documentation.unloadModule(jarFileName);
					x2server.LOG(Level.INFO,"[%s][%s] loading module classes %s",jarFileName, CC.logDateFormat.format(new java.util.Date(uploadDate.value)), newModuleList);
				}
				
				//the plugin id is contained in the jar file name and is the first part of its name.
				int pluginId = CCPluginManager.PLUGIN_ID(jarFileName);
				final Map<String,String> moduleConfig = getModuleConfiguration(pluginId);
				double pluginVersion = CCPluginManager.PLUGIN_VERSION(jarFileName);
				//at this point we will want to update the application metadata. (if we don't already know about it of course)
				X2ApplicationMetaData appMetadata = KNOWN_APPLICATIONS.stream().filter(x2app -> x2app.getId().equals(jarFileName)).findAny().orElse(new X2ApplicationMetaData());
				if(!KNOWN_APPLICATIONS.stream().anyMatch(x2app -> x2app.getId().equals(jarFileName))) {
					appMetadata.setEnabled(true);
					appMetadata.setId(jarFileName);
					appMetadata.setInstallDate(installDate.value == null ? uploadDate.value : installDate.value);
					appMetadata.setLastUpgradeDate(uploadDate.value);
					appMetadata.setName(PLUGIN_NAME(jarFileName));
					appMetadata.setVersion(pluginVersion);
					KNOWN_APPLICATIONS.add(appMetadata);
				}
				
				unloadModule(jarFileName); //we should un-load the module here.
				List<Map.Entry<String,String>> endpointList = moduleEndpointMap.entrySet().stream().filter((entry) -> { return entry.getValue().equals(jarFileName); }).collect(Collectors.toList());
				endpointList.stream().forEach((entry) -> { moduleEndpointMap.remove(entry.getKey()); });
				
				newModuleList.forEach((cls) -> {
					//each module will have to be instantiated and stored in the plugin cache for this instance.
					try {
						Module mod = Module.class.cast(x2cl.value.loadClass(cls).newInstance());
						ModuleMetaData metaData = new ModuleMetaData(pluginId, pluginVersion, mod.getClass().getSimpleName(), getModuleLogger(pluginId, pluginVersion, mod.getClass()));
						X2Module x2Module = new X2Module(mod, metaData);
						//register the module to recieve and process messages during the initialisation phase.
						Set<X2Module> moduleSet = LIVE_MODULE_MAP.get(jarFileName);
						if(moduleSet == null) {
							moduleSet = new HashSet<>();
							LIVE_MODULE_MAP.put(jarFileName, moduleSet);
						}
						moduleSet.add(x2Module);
						runWithModule(x2Module, x2server.getWORK_EXECUTOR(), (m) -> {
							mod.construct(metaData.getLog(), metaData.getName(), pluginId, pluginVersion);
							if(installDate.value == null) {
								mod.installed(); //we should actually only call this if this module version has never been installed.
							} else if(installDate.value < uploadDate.value){
								mod.upgraded();
							}
							//before we start we need to grab the configuration for the module and set it.
							mod.configure(moduleConfig);
							mod.start();
							appMetadata.getLimits().put(cls, X2ApplicationMetaData.X2ModuleLimits.wrap(mod.getLimits()));
							return null;
						},60);
						if(mod.getBasePath() != null) {
							final String endpoint = "/"+pluginId+"/v"+String.valueOf(pluginVersion)+(mod.getBasePath().startsWith("/") ? mod.getBasePath() : "/"+mod.getBasePath());
							appMetadata.getEndpoints().put(cls, endpoint);
							x2server.LOG(Level.INFO, "[%s][%f][%s] will process HTTP/HTTPS/WEBSOCKET requests from the base path: %s", String.valueOf(pluginId), pluginVersion, mod.getClass().getSimpleName(), endpoint);
							moduleEndpointMap.put(endpoint, jarFileName);
						}
						//does this implement a batch processor there is a way to check.
						if(x2Module.implementsBatch()) {
							appMetadata.getBatches().add(cls);
						}
						//does this implement an event processor there is a way to check.
						if(x2Module.implementsEvent()) {
							appMetadata.getEvents().add(cls);
						}
						//does this implement a fixed processor
						if(x2Module.implementsFixed()) {
							appMetadata.getFixed().add(cls);
						}
					}catch(ClassNotFoundException | IllegalAccessException clsNfEx) { //will never happen because we already checked above.
						x2server.LOG(Level.WARNING, "[%d][%s][%s] the module could not be loaded because of a class loading error: %s", pluginId, String.valueOf(pluginVersion), cls, clsNfEx.getMessage());
					}catch(NoClassDefFoundError clsErr) {
						x2server.LOG(Level.WARNING, "I was unable to initialize the module %s because I could not load a class it is dependant on %s please check that the dependancies are correct in your pom", cls, clsErr.getMessage());
					}catch(Throwable instEx) {
						x2server.LOG(Level.WARNING, "I was unable to initialize the class %s because of the error %s - {%s}", cls, instEx.getMessage(), CCError.toString(instEx));
					}
				});
				if(!newModuleList.isEmpty()) {
					CloudProvider.getInstance().getRuntime().setModuleInstallDate(jarFileName, System.currentTimeMillis());
					System.gc();
				}
			}catch(OutOfMemoryError memErr) {
				x2server.LOG(Level.SEVERE, "We have run out of memory. This is a fatal error");
				System.exit(0); //shutdown the jvm as there has been an out of memory error.
			}catch(RuntimeException rtEx) {
				if(rtEx.getCause() != null && rtEx.getCause() instanceof IOException && rtEx.getCause().getMessage().equals("No space left on device")) {
					x2server.LOG(Level.WARNING, "We have run out of space on disk. This means that functionality may be compromised cleaning temporary directory");
					//lets clear out the temporary directory to recover space.
					X2ClassLoader.clearTemporaryFiles();
				} else {
					x2server.LOG(Level.WARNING, "There was an error loading the module %s", CCError.toString(rtEx));
				}
			}
		}
	}
	
	@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@class", include = JsonTypeInfo.As.PROPERTY)
	public static class ModuleInfo implements Serializable {
		private int id = 0;
		private double version = 0;
		private String name = null;
		private String implementation = null;
		private boolean batch = false;
		
		public ModuleInfo() {}
		public ModuleInfo(int id,double version,String name,String implementation,boolean batch) {
			this.id = id;
			this.version = version;
			this.name = name;
			this.implementation = implementation;
			this.batch = batch;
		}

		public int getId() {
			return id;
		}

		public void setId(int id) {
			this.id = id;
		}

		public double getVersion() {
			return version;
		}

		public void setVersion(double version) {
			this.version = version;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getImplementation() {
			return implementation;
		}

		public void setImplementation(String implementation) {
			this.implementation = implementation;
		}

		public boolean isBatch() {
			return batch;
		}

		public void setBatch(boolean batch) {
			this.batch = batch;
		}

		@JsonIgnore
		@Override
		public int hashCode() {
			int hash = 7;
			hash = 71 * hash + this.id;
			hash = 71 * hash + (int) (Double.doubleToLongBits(this.version) ^ (Double.doubleToLongBits(this.version) >>> 32));
			hash = 71 * hash + Objects.hashCode(this.implementation);
			return hash;
		}

		@JsonIgnore
		@Override
		public boolean equals(Object obj) {
			if (this == obj) {
				return true;
			}
			if (obj == null) {
				return false;
			}
			if (getClass() != obj.getClass()) {
				return false;
			}
			final ModuleInfo other = (ModuleInfo) obj;
			if (this.id != other.id) {
				return false;
			}
			if (Double.doubleToLongBits(this.version) != Double.doubleToLongBits(other.version)) {
				return false;
			}
			if (!Objects.equals(this.implementation, other.implementation)) {
				return false;
			}
			return true;
		}
	}
	
	/**
	 * this method will store a list of running modules for this instance and will contain information as to whether they implement batch processing or not.
	 */
	protected void storeModuleList() {
		//once module loading is complete we need to save the list of modules running on this instance.
		//the list should contain the id of the module, the version of the module, the name of the module and if it contains a batch implementation.
		ModuleInfo[] moduleList = APPLICATION_LIST.stream()
			.filter(app -> PLUGIN_VALID(app.getId())) //only store modules which would be valid
			.flatMap(app -> Stream.concat(Stream.concat(app.getBatches().stream(), app.getEvents().stream()),app.getEndpoints().keySet().stream())
				.map(cls -> new ModuleInfo(PLUGIN_ID(app.getId()), PLUGIN_VERSION(app.getId()), PLUGIN_NAME(app.getId()), cls, !app.getBatches().isEmpty()))
			)
			.distinct()
			.toArray(ModuleInfo[]::new);
		CloudProvider.getInstance().getRuntime().store(x2server.getINSTANCE_ID()+".modulelist", moduleList);
	}
	
	public ModuleInfo[] getModuleList(String instanceId) {
		Object modList = CloudProvider.getInstance().getRuntime().retrieve(instanceId+".modulelist", CC.classOf(new ModuleInfo[] {}));
		return (ModuleInfo[])modList;
	}
	
	//we need a method that will give us all of the active instances at a specific location.
	public List<String> listInstances(String location) {
		//we can do this by getting a list of the queue id's and filtering that list based on the location parameter.
		return CloudProvider.getInstance().getRuntime().listQueueIds(location).stream().map(qId -> qId.substring(qId.lastIndexOf('/')+("CC-"+location+"-").length()+1)).parallel()
			.filter(inst -> isInstanceActive(inst))
			.collect(Collectors.toList());
	}
	
	protected boolean isInstanceActive(String instanceId) {
		Long lastPollDate = CloudProvider.getInstance().getRuntime().retrieve(instanceId+".lastpoll", Long.class);
		//if this was at least 5 minutes ago then we can keep it in this list.
		return (lastPollDate != null && System.currentTimeMillis()-TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES) < lastPollDate);
	}
	
	/**
	 * this method will return a reference to the application identified by the module id and version passed.
	 * if the version is set to 0 then the most recent version of the module will be returned.
	 * 
	 * @param moduleId the id of the module to look for
	 * @param version the version of the module to look for or 0 for the latest version
	 * @return a reference to the application metadata for the module and version provided or null if an application which meets the criteria cannot be found.
	 */
	public X2ApplicationMetaData getApplication(int moduleId,double version) {
		return getApplicationList().stream()
			.filter(app -> PLUGIN_ID(app.getId()) == moduleId && (version == 0 || PLUGIN_VERSION(app.getId()) == version))
			.max((app1,app2) -> new Double(PLUGIN_VERSION(app1.getId())).compareTo(PLUGIN_VERSION(app2.getId())))
			.orElse(null);
	}
	
	public ModuleLimit getModuleLimits(int moduleId,double version,String moduleClass) {
		X2ApplicationMetaData app = getApplication(moduleId, version);
		if(app != null && app.getLimits() != null) {
			return app.getLimits().get(moduleClass);
		}
		
		return null;
	}
	
	/**
	 * this method will return a list of the currently active and running modules across all of the instances 
	 * targeted at a specific location in the cluster.
	 * 
	 * Please note this information will be retrieved from the live cluster at maximum every 5 minutes.
	 * 
	 * @param location the location at which to fetch all of the active code modules.
	 * @return a list of the modules which have active code running somewhere in the cluster.
	 */
	public synchronized Set<ModuleInfo> getLiveModuleList(String location) {
		ModuleInfoCache cachedInfo = LIVE_MODULE_CACHE.get(location);
		if(cachedInfo != null && !cachedInfo.isExpired()) {
			//the cache is valid.
			return cachedInfo.getActiveModules();
		} else {
			CopyOnWriteArraySet<ModuleInfo> activeModuleList = new CopyOnWriteArraySet<>();
			listInstances(location).parallelStream().forEach(inst -> {
				activeModuleList.addAll(Arrays.asList(getModuleList(inst)));
			});
			if(!activeModuleList.isEmpty()) {
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
			.map(q -> q.substring(q.lastIndexOf("CC-")))
			.map(q -> q.substring(3,q.length()-("WORK-QUEUE".length()+1)))
			.collect(Collectors.toSet());
	}
	/**
	 * this method will return the list of locations available for this GSM.
	 * the list of locations will be calculated based on the number of active instances
	 * discovered through access to the message queue. we can use the .lastpoll file on S3
	 * to discover if an instance is active or not.
	 * 
	 * to constrain resource usage the location list will be stored in a cache for 5 minutes after which
	 * it will naturally expire and will be re-calculated if needed.
	 * 
	 * @return a set containing the unique list of active locations in this GSM.
	 */
	public synchronized Set<String> getActiveLocationList() {
		return CloudProvider.getInstance().getRuntime().listQueueIds(null).stream()
			.filter(q -> !q.toUpperCase().endsWith("WORK-QUEUE"))
			.map(q -> q.substring(q.lastIndexOf("CC-")))
			.filter(q -> isInstanceActive(q.substring(q.length()-36)))
			.map(q -> q.substring(3,q.length()-37))
			.collect(Collectors.toSet());
	}
	
	public synchronized Set<String> getActiveInstanceList() {
		return CloudProvider.getInstance().getRuntime().listQueueIds(null, false).stream()
			.map(q -> q.substring(q.lastIndexOf("CC-")))
			.map(q -> q.substring(q.length()-36))
			.filter(inst -> isInstanceActive(inst))
			.collect(Collectors.toSet());
	}
	
	public synchronized Set<String> getInstanceLocations(String... instances) {
		final List<String> instanceList = Arrays.asList(instances);
		return CloudProvider.getInstance().getRuntime().listQueueIds(null, false).stream()
			.map(q -> q.substring(q.lastIndexOf("CC-")))
			.filter(q -> instanceList.contains(q.substring(q.length()-36)))
			.map(q -> q.substring(3,q.length()-37))
			.collect(Collectors.toSet());
	}
	
	public synchronized Map<String,String> getInstanceLocationMap(String... instances) {
		final List<String> instanceList = Arrays.asList(instances);
		return CloudProvider.getInstance().getRuntime().listQueueIds(null, false).stream()
			.map(q -> q.substring(q.lastIndexOf("CC-")))
			.filter(q -> instanceList.contains(q.substring(q.length()-36)))
			.map(q -> new KeyValue<>(q.substring(q.length()-36), q.substring(3,q.length()-37)))
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
	public synchronized void startFixedProcess(final X2Module module,final String applicationId) {
		final String watchdogId = UUID.randomUUID().toString();
		final Future<Object> fixedTask = buildModuleFuture(module, x2server.getWORK_EXECUTOR(), (mod) -> {
			//write the file that says that we are active.
			CloudProvider.getInstance().getRuntime().write(CC.SYSTEM_STORAGE_PATH, "lastactive-"+mod.getModule().getClass().getName()+"-"+x2server.getINSTANCE_ID()+"-"+applicationId, System.currentTimeMillis());
			x2server.LOG(Level.INFO, "[%s][%s] - STARTING FIXED PROCESS",applicationId,module.getModule().getClass().getName());
			module.getModule().processFixed(x2server.getLOCATION(), x2server.getINSTANCE_ID());
			module.shutdownWatchdog(watchdogId);
			CloudProvider.getInstance().getRuntime().remove(CC.SYSTEM_STORAGE_PATH, "lastactive-"+mod.getModule().getClass().getName()+"-"+x2server.getINSTANCE_ID()+"-"+applicationId);
			x2server.LOG(Level.INFO, "[%s][%s] - FINISHED FIXED PROCESS",applicationId,module.getModule().getClass().getName());
			return null;
		});
		//we will now monitor this task with a background thread
		
		final ScheduledFuture watchdog = x2server.getSCHEDULER().scheduleAtFixedRate(() -> {
			if(!fixedTask.isDone()) {
				CloudProvider.getInstance().getRuntime().write(CC.SYSTEM_STORAGE_PATH, "lastactive-"+module.getModule().getClass().getName()+"-"+x2server.getINSTANCE_ID()+"-"+applicationId, System.currentTimeMillis());
			} else {
				module.shutdownWatchdog(watchdogId);
			}
		}, 3, 3, TimeUnit.MINUTES);
		module.addWatchdog(watchdogId, watchdog);
	}
}
