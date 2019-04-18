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
package org.eclipse.jemo;

import org.eclipse.jemo.api.JemoParameter;
import org.eclipse.jemo.internal.model.*;
import org.eclipse.jemo.sys.*;
import org.eclipse.jemo.sys.JemoPluginManager;
import org.eclipse.jemo.sys.auth.JemoAuthentication;
import org.eclipse.jemo.sys.internal.JemoNullOutputStream;
import org.eclipse.jemo.sys.internal.ManagedFunction;
import org.eclipse.jemo.sys.internal.ManagedFunctionWithException;
import org.eclipse.jemo.sys.internal.Util;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;

import java.io.*;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.*;
import java.util.logging.Formatter;

/**
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public abstract class AbstractJemo {

    /**
     * this is a reference to the first instance of the abstract Jemo server started within this JVM.
     * if an this instance is stopped then it will be replaced with the first server that starts.
     **/
    public static volatile AbstractJemo DEFAULT_INSTANCE = null;

    /**
     * definition of shared public variables
     **/
    public static final String LOCATION_AWS = "AWS";
    public static final String LOCATION_GCP = "GCP";
    public static final String LOCATION_AZURE = "AZURE";
    public static final String LOCATION_HEROKU = "HEROKU";
    public static final String[] CLOUD_LOCATIONS = new String[]{LOCATION_AWS, LOCATION_AZURE, LOCATION_GCP, LOCATION_HEROKU};

    public static final String SYSTEM_STORAGE_PATH = "_sys_jemo_runtime";
    public static final String LOCK_FILE_FIXED_PROCESS = "_sys_jemo_all_gsm_lock_fixed_process";
    public static final String GLOBAL_QUEUE_NAME = "JEMO-GLOBAL-WORK-QUEUE";
    public static final String GLOBAL_TOPIC_NAME = "JEMO-GLOBAL-NOTIFICATION";
    public static final SimpleDateFormat logDateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS");

    protected static final ObjectMapper mapper = Util.mapper;

    private JemoLogFormatter LOG_FORMATTER = null;

    private String INSTANCE_ID = null;
    private String INSTANCE_QUEUE_URL = null;
    private String GLOBAL_QUEUE_URL = null;
    private String LOCATION_QUEUE_URL = null;

    private String LOCATION;

    private String LOCATION_QUEUE_NAME;
    private int JEMO_HTTPS_PORT;
    private int JEMO_HTTP_PORT;
    private JemoHTTPConnector.MODE JEMO_HTTP_MODE;

    private Set<Integer> PLUGIN_WHITELIST;
    private Set<Integer> PLUGIN_BLACKLIST;
    private long QUEUE_POLL_WAIT_TIME;
    private boolean IN_CLOUD_LOCATION;

    //we need to add support for module blacklisting (so we can avoid having things with a single module with a certain name loaded at all)

    /**
     * private references to queue listening threads
     */
    private JemoQueueListener instanceQueueListener = null;
    private JemoQueueListener locationQueueListener = null;
    private JemoQueueListener globalQueueListener = null;
    private final ThreadGroup queueListenerGroup = new ThreadGroup("QUEUE-LISTENERS");
    private final CopyOnWriteArrayList<JemoQueueListener> QUEUE_LISTENERS = new CopyOnWriteArrayList<>();
    private JemoScheduler instanceScheduler = null;

    /**
     * references to the system logger
     */
    private final Logger SYS_LOGGER;
    private JemoPluginManager pluginManager = null;
    private JemoHTTPConnector httpServer = null;
    private final ExecutorService WORK_EXECUTOR = Executors.newFixedThreadPool(50);
    private final ScheduledExecutorService SCHEDULER = Executors.newScheduledThreadPool(5); //we will increase this from 2 to 5 to accomodate for the fixed process monitoring tasks.
    private volatile long lastSchedulerRun = System.currentTimeMillis();
    private String HOSTNAME = null;
    private final long SYS_STARTUP_TIME = System.currentTimeMillis();
    private final Set<String> MODULE_BATCH_BLACKLIST = new CopyOnWriteArraySet<>();
    private volatile long LAST_SCHEDULER_POLL = 0;
    private boolean started = false;

    private volatile long lastBatchRunDate = System.currentTimeMillis();
    private final AtomicBoolean batchRunning = new AtomicBoolean(false);

    /**
     * Flag used to denote if the current run is in installation mode.
     * If true, it means certain parts of the application are not initialized yet.
     */
    private static boolean IS_IN_INSTALLATION_MODE = false;

    private static final String PARAM_SET_NAME = "PARAM_SET_NAME";

    //the default constructor should set all of the things that would have been pulled in via system parameters.
    protected AbstractJemo(final String location, final int httpsPort, final int httpPort,
                           final String moduleWhitelist, final String moduleBlacklist, final long queuePollWaitTime, final boolean inCloudLocation,
                           final boolean printLogs, final String logFilePath, final Level logLevel, final String UUID_FILE_NAME) {
        IS_IN_INSTALLATION_MODE = false;
        Util.B(null, x -> {
            File uuidFile = new File(System.getProperty("java.io.tmpdir"), UUID_FILE_NAME); //we need to support different UUID's per instance
            if (uuidFile.exists()) {
                FileInputStream fin = new FileInputStream(uuidFile);
                INSTANCE_ID = toString(fin);
            } else {
                INSTANCE_ID = UUID.randomUUID().toString();
                FileOutputStream fout = new FileOutputStream(uuidFile);
                fout.write(INSTANCE_ID.getBytes("UTF-8"));
                fout.close();
            }
        });
        SYS_LOGGER = Logger.getLogger("JEMO-{" + INSTANCE_ID + "}");
        this.LOCATION = location;
        this.LOCATION_QUEUE_NAME = "JEMO-" + LOCATION + "-WORK-QUEUE";
        this.JEMO_HTTPS_PORT = httpsPort;
        this.JEMO_HTTP_PORT = httpPort;
        this.JEMO_HTTP_MODE = JemoHTTPConnector.MODE.HTTPS;
        this.PLUGIN_WHITELIST = Util.parseIntegerRangeDefinition(moduleWhitelist);
        this.PLUGIN_BLACKLIST = Util.parseIntegerRangeDefinition(moduleBlacklist);
        this.QUEUE_POLL_WAIT_TIME = queuePollWaitTime;
        this.IN_CLOUD_LOCATION = inCloudLocation;

        //setup the system logging instance.
        SYS_LOGGER.setUseParentHandlers(false);
        ConsoleHandler sysLogHandler = buildConsoleHandler(printLogs, logFilePath);
        sysLogHandler.setLevel(logLevel);
        SYS_LOGGER.addHandler(sysLogHandler);
        SYS_LOGGER.setLevel(logLevel);
    }

    JemoConsoleHandler buildConsoleHandler(boolean printLogs, String logFilePath) {
        return new JemoConsoleHandler() {
            private boolean isInitalized;

            @Override
            protected boolean isPrintLogs() {
                return printLogs;
            }

            @Override
            protected String getLogFilePath() {
                return logFilePath;
            }

            @Override
            public Formatter getFormatter() {
                if (!isInitalized) {
                    isInitalized = true;
                    LOG_FORMATTER = new JemoLogFormatter(LOCATION, getHOSTNAME(), INSTANCE_ID);
                }
                return LOG_FORMATTER;
            }
        };
    }

    abstract class JemoConsoleHandler extends ConsoleHandler {

        public JemoConsoleHandler() {
            super();
        }

        @Override
        public Formatter getFormatter() {
            return getLOG_FORMATTER();
        }

        @Override
        protected synchronized void setOutputStream(OutputStream out) throws SecurityException {
            if (!isPrintLogs()) {
                super.setOutputStream(new JemoNullOutputStream()); //To change body of generated methods, choose Tools | Templates.
            } else {
                if (getLogFilePath() == null) {
                    super.setOutputStream(out);
                } else {
                    try {
                        super.setOutputStream(new FileOutputStream(getLogFilePath(), false));
                    } catch (FileNotFoundException fnfEx) {
                    }
                }
            }
        }

        protected abstract boolean isPrintLogs();

        protected abstract String getLogFilePath();
    }

    private void resetLogInstance(boolean printLogs, String logFilePath, Level logLevel) {
        SYS_LOGGER.removeHandler(getConsoleHandler());
        final Handler handler = buildConsoleHandler(printLogs, logFilePath);
        handler.setLevel(logLevel);
        SYS_LOGGER.addHandler(handler);
        SYS_LOGGER.setLevel(logLevel);
    }

    public Handler getConsoleHandler() {
        return SYS_LOGGER.getHandlers()[0];
    }

    private static String readParamSetNameFromPropertiesFile() {
        final Properties properties = Util.readPropertiesFile();
        return properties == null ? null : properties.getProperty(PARAM_SET_NAME);
    }

    public void storeParamSet(String paramSetName) {
        Properties properties = Util.readPropertiesFile();
        if (properties == null) {
            properties = new Properties();
        }
        properties.setProperty(PARAM_SET_NAME, paramSetName);
        Util.storePropertiesFile(properties);
    }

    private void readParameters(CloudRuntime cloudRuntime) {
        String parameterSetName = readParameterSetName(cloudRuntime);
        // If no parameter set name is found return, we won't overwrite the default values.
        if (parameterSetName == null) {
            LOG("No parameter set name was found. The default parameter values are honoured.", Level.INFO);
            return;
        } else {
            LOG("Parameter set name was found: " + parameterSetName, Level.INFO);
        }

        final Map<String, String> parameterSet = cloudRuntime.read(Map.class, "param_set", parameterSetName);
        resetParameters(cloudRuntime, parameterSet);
    }

    public void resetParameters(CloudRuntime cloudRuntime, Map<String, String> parameterSet) {
        if (parameterSet == null) {
            LOG("No parameter set was found. The default parameter values are honoured.", Level.INFO);
            return;
        }
        final String newLocation = parameterSet.get(JemoParameter.LOCATION.label());
        if (!this.LOCATION.equals(newLocation)) {
            //check of queues of this instance in a different location and delete them.
            cloudRuntime.listQueueIds(null, false).stream()
                    .filter(qId -> qId.contains(this.INSTANCE_ID) &&
                            !qId.toUpperCase().endsWith("JEMO-" + newLocation + "-" + this.INSTANCE_ID))
                    .forEach(qId -> cloudRuntime.deleteQueue(qId));
        }
        this.LOCATION = newLocation;
        this.LOCATION_QUEUE_NAME = "JEMO-" + LOCATION + "-WORK-QUEUE";
        this.PLUGIN_WHITELIST = Util.parseIntegerRangeDefinition(parameterSet.get(JemoParameter.MODULE_WHITELIST.label()));
        this.PLUGIN_BLACKLIST = Util.parseIntegerRangeDefinition(parameterSet.get(JemoParameter.MODULE_BLACKLIST.label()));
        this.QUEUE_POLL_WAIT_TIME = Long.parseLong(parameterSet.get(JemoParameter.QUEUE_POLL_TIME.label()));
        this.IN_CLOUD_LOCATION = "CLOUD".equals(parameterSet.get(JemoParameter.LOCATION_TYPE.label()));

        final boolean logLocal = Boolean.parseBoolean(parameterSet.get(JemoParameter.LOG_LOCAL.label()));
        final String logOutput = parameterSet.get(JemoParameter.LOG_OUTPUT.label());
        final Level logLevel = Level.parse(parameterSet.get(JemoParameter.LOG_LEVEL.label()));

        parameterSet.forEach((k, v) -> {
            if (!"name".equals(k)) {
                LOG(Level.INFO, String.format("[%s] Parameter initialized, [%s] = [%s]\n", AbstractJemo.class.getSimpleName(), k, v));
            }
        });

        resetLogInstance(logLocal, logOutput, logLevel);
        cloudRuntime.resetLogConsoleHandler(getConsoleHandler());
    }

    private String readParameterSetName(CloudRuntime cloudRuntime) {
        // First, look for a JVM variable
        if (System.getProperty(PARAM_SET_NAME) != null) {
            return System.getProperty(PARAM_SET_NAME);
        }

        // Second, look if there is a variable in the properties file, if the properties file exists.
        String paramSetName = readParamSetNameFromPropertiesFile();
        if (paramSetName != null) {
            return paramSetName;
        }

        // Third, look for an OS env variable with this name
        if (System.getenv(PARAM_SET_NAME) != null) {
            return System.getenv(PARAM_SET_NAME);
        }

        final String hostname = System.getenv("HOSTNAME");
        LOG(Level.INFO, "hostname: [%s]", hostname);
        if (hostname != null) {
            final String[] split = hostname.split("-");
            final String id = split[split.length - 1];
            final Map<String, String> containerIdToParamSet = cloudRuntime.read(Map.class, "containers_per_paramset", "container_id_to_paramset");
            if (containerIdToParamSet != null && containerIdToParamSet.get(id) != null) {
                return containerIdToParamSet.get(id);
            }
        }

        return cloudRuntime.readInstanceTag(PARAM_SET_NAME);
    }

    public synchronized void start() throws Throwable {
        if (started) {
            return;
        }
        if (DEFAULT_INSTANCE == null) {
            DEFAULT_INSTANCE = this;
        }
        //set the default timezone for the application to london
        long start = System.currentTimeMillis();
        TimeZone.setDefault(TimeZone.getTimeZone("Europe/London"));
        //so according to the architecture spec the first thing we should do is to create a unique instance identifier
        //for this running version of Jemo
        //before we calculate a new UUID for this instance lets check if we already have an id saved on our temporary storage for this instance.

        LOG(Level.INFO, "Jemo {%s} - {" + INSTANCE_ID + "} startup initiated", JemoRuntimeVersion.getVersion());
        if (LOCATION != null && LOCATION_RESERVED(LOCATION)) {
            LOG(Level.INFO, "Specified Location: [%s] is invalid as the location key specified is reserved", LOCATION);
            System.exit(-1);
        }

        // By creating the runtime object we check if the GSM has been setup yet, if it has not then we should tell the user to go into
        // some kind of setup mode so this can be configured. It is the responsibility of the runtime class constructor to trigger this validation.
        final CloudProvider provider = CloudProvider.getInstance();
        IS_IN_INSTALLATION_MODE = provider == null;
        if (IS_IN_INSTALLATION_MODE) {
            int port = JEMO_HTTP_MODE == JemoHTTPConnector.MODE.HTTP ? JEMO_HTTP_PORT : JEMO_HTTPS_PORT;
            final String logMessage = String.format("GSM is not setup yet. Please click on the following link to provide configuration: %s", JEMO_HTTP_MODE.name().toLowerCase() + "://localhost:" + port + "/jemo/setup/");
            LOG(Level.WARNING, logMessage);
            if (System.getProperty(JemoParameter.LOG_LOCAL.label()) != null) {
                System.out.println(logMessage);
            }
        } else {
            onSuccessfulValidation(provider.getRuntime());
        }

        //http comes next as we need an operational system for synchronous processing before asynchronous processing can begin.
        httpServer = new JemoHTTPConnector(JEMO_HTTPS_PORT, JEMO_HTTP_PORT, this);

        //all cloudreach connect instances will respond on the https/http port to requests so we need to startup an embedded jetty instance
        //to handle those requests
        Runtime.getRuntime().addShutdownHook(new Thread(() -> Util.B(null, x -> AbstractJemo.this.stop())));

        //initialise internal scheduler service.
        //we should review the way this works as the reliability of scheduled execution is debateable. The mechanism also created a log of background noice and message processing
        //I am certain that a better way to solve this problem can be found.
        //when we startup we need to make sure someone is is holding a valid nomination and if nobody is then we need to nominate someone and start the batch processor.
        instanceScheduler = new JemoScheduler(this);
        instanceScheduler.start();
		
		/*SCHEDULER.scheduleWithFixedDelay(() -> {
			LAST_SCHEDULER_POLL = System.currentTimeMillis();
			Calendar cal = Calendar.getInstance();
			//run only if this happens on second 0
			if(cal.get(Calendar.SECOND) == 0) {
				//1. lets get the last modified date on the last time the poll was actually done.
				try {
					Long LAST_NOMINATION = CloudProvider.getInstance().getRuntime().retrieve("SYS-SCHEDULER-NOMINATION-TIMESTAMP", Long.class);
					if(LAST_NOMINATION == null) {
						//lets just create the last nomination reference using our own current time.
						CloudProvider.getInstance().getRuntime().store("SYS-SCHEDULER-NOMINATION-TIMESTAMP", System.currentTimeMillis());
					} else if(System.currentTimeMillis()-LAST_NOMINATION > TimeUnit.MILLISECONDS.convert(30, TimeUnit.SECONDS)) {
						//a minute has passed since the last nomination.
						//2. lets write ourselves as the nominated party.
						CloudProvider.getInstance().getRuntime().store("SYS-SCHEDULER-NOMINEE", INSTANCE_ID);
						//3. wait 10 seconds for everyone else to get their nominations in.
						try { Thread.sleep(10000); } catch(InterruptedException ex) {} //wait 10 seconds before actually running anything.
						//4. read who was nominated
						String nominee = CloudProvider.getInstance().getRuntime().retrieve("SYS-SCHEDULER-NOMINEE", String.class);
						if(nominee.equals(INSTANCE_ID)) {
							//we have been nominated. now get a list of all the locations in this GSM
							CopyOnWriteArrayList<String> locationList = new CopyOnWriteArrayList<>();
							CopyOnWriteArrayList<String> failedLocationList = new CopyOnWriteArrayList<>();
							CloudProvider.getInstance().getRuntime().listQueueIds(null).stream().map(q -> {
									return q.substring(q.toUpperCase().lastIndexOf("JEMO-")).split("\\-")[1]; //the search routine was set to be case insensitive because azure queues are lowercase.
								}).collect(Collectors.toSet()).parallelStream().forEach(loc -> {
								//the send could actually fail for many reasons so if it does we should not abort the entire operation.
								try {
									getPluginManager().runWithModuleContext(Void.class, x -> {
										JemoMessage sysRunScheduleMsg = new JemoMessage();
										sysRunScheduleMsg.setModuleClass(Jemo.class.getName());
										sysRunScheduleMsg.send(loc);
										locationList.add(loc);
										
										return null;
									});
								}catch(Throwable ex) {
									failedLocationList.add(loc);
								}
							});
							if(!locationList.isEmpty()) {
								long lastNomination = System.currentTimeMillis();
								CloudProvider.getInstance().getRuntime().store("SYS-SCHEDULER-NOMINATION-TIMESTAMP", lastNomination);
								LOG(Level.INFO,"Scheduler Engine Run Successfully. Last Nomination on %s - successful locations %s - failed locations %s The elapsed time since the last nomination was %s", logDateFormat.format(new java.util.Date(lastNomination)),
										locationList.toString(),failedLocationList.toString(), Util.getTimeString(lastNomination-LAST_NOMINATION));
							} else {
								LOG(Level.WARNING, "Scheduler Engine Run Failed. failed locations where %s. This is likely to be caused by a network or other temporary error", failedLocationList.toString());
							}
						}
					}
				}catch(Throwable ex) {
					LOG(Level.INFO,"Error running scheduler beat: %s", JemoError.toString(ex));
				}
			}
		}, TimeUnit.SECONDS.convert(5, TimeUnit.MINUTES), 1, TimeUnit.SECONDS);*/
        SCHEDULER.scheduleWithFixedDelay(new JemoWatchdog(this), 1, 15, TimeUnit.MINUTES);
        LOG("Jemo - {" + INSTANCE_ID + "} startup completed - startup time {" + (System.currentTimeMillis() - start) + "ms}", Level.INFO);
        started = true;
    }

    /**
     * This method needs to be called after the credentials and permissions validation has passed.
     *
     * @param runtime the runtime object
     * @throws NoSuchAlgorithmException
     * @throws IOException
     */
    public void onSuccessfulValidation(CloudRuntime runtime) throws NoSuchAlgorithmException, IOException {
        long start = System.currentTimeMillis();
        runtime.start(this);

        readParameters(runtime);

        //we need to check for the global queue and create it if it does not exist
        //to improve the startup time lets make sure these queues exist before requesting they be created.
        GLOBAL_QUEUE_URL = runtime.defineQueue(GLOBAL_QUEUE_NAME);
        LOCATION_QUEUE_URL = runtime.defineQueue(LOCATION_QUEUE_NAME);

        final String newInstanceQueue = runtime.createInstanceQueue(LOCATION, INSTANCE_ID);

        boolean isQueUrlChanged = !Objects.equals(newInstanceQueue, INSTANCE_QUEUE_URL);
        INSTANCE_QUEUE_URL = newInstanceQueue;
        LOG(String.format("Jemo - QUEUE SYSTEM INITIALIZED in %d (ms)", System.currentTimeMillis() - start), Level.INFO);

        if (instanceQueueListener == null) {
            //we need a set of queue listeners that will poll for messages on any of the queues, these messages will be taken off the queue
            //at a set processing rate and the processed in dedicated threads on the system which will free up the work processing thread
            //to work against more messages incomming.
            instanceQueueListener = new JemoQueueListener(queueListenerGroup, INSTANCE_QUEUE_URL, this);
            locationQueueListener = new JemoQueueListener(queueListenerGroup, LOCATION_QUEUE_URL, this);
            globalQueueListener = new JemoQueueListener(queueListenerGroup, GLOBAL_QUEUE_URL, this);

            QUEUE_LISTENERS.add(globalQueueListener);
            QUEUE_LISTENERS.add(locationQueueListener);
            QUEUE_LISTENERS.add(instanceQueueListener);

            //every instance will also be connected to the global work queue which will have a list of the tasks to process by the entire system.
            QUEUE_LISTENERS.parallelStream().forEach(JemoQueueListener::start);
        } else if (isQueUrlChanged) {
            // Any change in the LOCATION affects the INSTANCE_QUEUE_URL and the LOCATION_QUEUE_URL
            instanceQueueListener.interrupt();
            locationQueueListener.interrupt();
            QUEUE_LISTENERS.remove(instanceQueueListener);
            QUEUE_LISTENERS.remove(locationQueueListener);

            instanceQueueListener = new JemoQueueListener(queueListenerGroup, INSTANCE_QUEUE_URL, this);
            locationQueueListener = new JemoQueueListener(queueListenerGroup, LOCATION_QUEUE_URL, this);

            QUEUE_LISTENERS.add(instanceQueueListener);
            QUEUE_LISTENERS.add(locationQueueListener);

            //every instance will also be connected to the global work queue which will have a list of the tasks to process by the entire system.
            QUEUE_LISTENERS.parallelStream()
                    .filter(listener -> listener != globalQueueListener)
                    .forEach(JemoQueueListener::start);
        }

        //we should initialize the authentication layer before starting the plugin manager as all plugins will require authorisation
        JemoAuthentication.init(this);

        IS_IN_INSTALLATION_MODE = false;

        //we need to initialize our plugins/modules first and once they are there we will receive messages which they can process as a result
        pluginManager = new JemoPluginManager(this);
    }

    public synchronized void stop() throws Exception {
        if (started) {
            LOG("Starting shutdown sequence", Level.INFO);
            this.instanceScheduler.interrupt();
            SCHEDULER.shutdownNow();
            if (DEFAULT_INSTANCE != null && DEFAULT_INSTANCE.getINSTANCE_ID().equals(getINSTANCE_ID())) {
                DEFAULT_INSTANCE = null;
            }
            httpServer.stop();
            //after we stop the http server we should also stop all of the active modules.
            getPluginManager().getLoadedModules()
                    .parallelStream()
                    .forEach(m -> m.getModule().stop());

            queueListenerGroup.interrupt();
            long totalWait = 0;
            while (queueListenerGroup.activeCount() != 0) {
                TimeUnit.MILLISECONDS.sleep(150);
                totalWait += 150;
                if (totalWait >= 10000) {
                    break;
                }
            }
            if (queueListenerGroup.activeCount() == 0 && !queueListenerGroup.isDestroyed()) {
                queueListenerGroup.destroy();
            }
            LOG("The instance queue: " + INSTANCE_QUEUE_URL + " will be deleted", Level.INFO);
            final CloudProvider cloudProvider = CloudProvider.getInstance();
            if (cloudProvider != null) {
                cloudProvider.getRuntime().deleteQueue(INSTANCE_QUEUE_URL);
            }
            SCHEDULER.awaitTermination(5, TimeUnit.SECONDS);
            //we also need to stop the queue listeners and other stuff.
            started = false;
        }
    }

    public ExecutorService getEVENT_EXECUTOR() {
        return WORK_EXECUTOR;
    }

    public static final boolean LOCATION_RESERVED(String location) {
        switch (location) {
            case JemoMessage.LOCATION_ANYWHERE:
            case JemoMessage.LOCATION_CLOUD:
            case JemoMessage.LOCATION_LOCALLY:
                return true;
        }

        return false;
    }

    public final boolean sys_IS_CLOUD_LOCATION(String location) {
        return IN_CLOUD_LOCATION || Arrays.asList(CLOUD_LOCATIONS).contains(location);
    }

    public final void LOG(String message, Level logLevel) {
        SYS_LOGGER.log(logLevel, message);
    }

    public void LOG(Level logLevel, String message, Object... args) {
        SYS_LOGGER.log(logLevel, message, args);
    }

    public static final String toJSONString(Object obj) throws JsonProcessingException {
        return Util.toJSONString(obj);
    }

    public static final String _safe_toJSONString(Object obj) {
        try {
            return toJSONString(obj);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    public static final <T extends Object> T fromJSONString(Class<T> cls, String jsonString) throws IOException {
        return Util.fromJSONString(cls, jsonString);
    }

    public static <T extends Object> List<T> fromJSONArray(Class<T> cls, String jsonData) throws IOException {
        if (jsonData == null) {
            return null;
        }

        return mapper.readValue(jsonData, mapper.getTypeFactory().constructCollectionType(List.class, cls));
    }

    public final List<String> runBatch() {
        return runBatch(-1, -1, null);
    }

    public final List<String> runBatch(int moduleId, double moduleVersion, String moduleImplementation) {
        CopyOnWriteArrayList<String> moduleBatchList = new CopyOnWriteArrayList<>();
        pluginManager.getLiveModuleList(LOCATION).stream()
                .filter(m -> m.isBatch() && !MODULE_BATCH_BLACKLIST.contains(m.getId() + ":" + m.getVersion() + ":" + m.getImplementation()))
                .filter(m -> moduleImplementation == null || (m.getId() == moduleId && m.getVersion() == moduleVersion && m.getImplementation().equals(moduleImplementation)))
                .parallel()
                .forEach(m -> {
                    Util.B(null, y -> {
                        pluginManager.runWithModuleContext(Void.class, x -> {
                            JemoMessage scheduleMsg = new JemoMessage();
                            scheduleMsg.setModuleClass(Jemo.class.getName());
                            scheduleMsg.setPluginId(m.getId());
                            scheduleMsg.getAttributes().put("module_class", m.getImplementation());
                            scheduleMsg.setPluginVersion(moduleVersion);
                            scheduleMsg.send(JemoMessage.LOCATION_LOCALLY);
                            return null;
                        });
                        moduleBatchList.add(String.format("MODULE:[id: %d, version: %s, name: %s, implementation: %s]", m.getId(), m.getVersion(), m.getName(), m.getImplementation()));
                    });
                });

        CloudProvider.getInstance().getRuntime().store("SYS-SCHEDULER-LASTRUN-" + LOCATION, System.currentTimeMillis());

        return moduleBatchList;
    }

    public void sendRunBatchMessage(int moduleId, String moduleImplementation, double version, String targetInstanceQueueUrl) {
        Util.B(null, y -> {
            pluginManager.runWithModuleContext(Void.class, x -> {
                JemoMessage scheduleMsg = new JemoMessage();
                scheduleMsg.setModuleClass(Jemo.class.getName());
                scheduleMsg.setPluginId(moduleId);
                scheduleMsg.getAttributes().put("module_class", moduleImplementation);
                scheduleMsg.setPluginVersion(version);
                scheduleMsg.send(targetInstanceQueueUrl);
                return null;
            });
        });
    }

    public final void sys_processMessage(JemoMessage msg) throws Throwable {
        //implement the scheduler
        msg.setCurrentInstance(INSTANCE_ID);
        msg.setCurrentLocation(LOCATION);
        if (msg.getModuleClass() != null && msg.getModuleClass().equalsIgnoreCase(Jemo.class.getName())) {
            //this is an internal system communication.
            if (msg.getAttributes().containsKey("scheduler_run_time")) {
                lastSchedulerRun = Long.class.cast(msg.getAttributes().get("scheduler_run_time"));
            } else if (msg.getAttributes().containsKey("module_class")) {
                //this means we should actually run the batch processor on this module (one may not even be defined).
                msg.setModuleClass((String) msg.getAttributes().get("module_class"));
                try {
                    pluginManager.process(msg);
                } catch (Throwable ex) {
                    if (!(ex instanceof TooMuchWorkException)) {
                        LOG(Level.WARNING, "[%d][%s] Failed Batch Job: %s", new Object[]{msg.getPluginId(), msg.getModuleClass(), JemoError.toString(ex)});
                    }
                }
            } else if (msg.getAttributes().containsKey(JemoVirtualHostManager.EVENT_RELOAD)) {
                pluginManager.loadVirtualHostDefinitions();
                LOG(Level.INFO, "Virtual Host Definitions Reloaded");
            } else if (!batchRunning.compareAndSet(false, true)) { //use an atomic boolean here
                List<String> moduleBatchList = new ArrayList<>();
                CopyOnWriteArrayList<String> failedModuleBatchList = new CopyOnWriteArrayList<>();
                Long previousLocationRunDate = null;
                long currentLocationRunDate = 0;
                try {
                    lastBatchRunDate = System.currentTimeMillis();
                    //load the last time a batch was run at this location
                    previousLocationRunDate = CloudProvider.getInstance().getRuntime().retrieve("SYS-SCHEDULER-LASTRUN-" + LOCATION, Long.class);
                    //ensure that if we recieve a batch run command to early we ignore it.
                    //we also want to ensure we don't run any batches on this instance until the instance has been running for at least 5 minutes.
                    if (System.currentTimeMillis() - SYS_STARTUP_TIME >= TimeUnit.MILLISECONDS.convert(5, TimeUnit.MINUTES) &&
                            (previousLocationRunDate == null || lastBatchRunDate - previousLocationRunDate > TimeUnit.MILLISECONDS.convert(50, TimeUnit.SECONDS))) {
                        //the issue that we have in this setup is that we don't really know which instances are running which modules
                        //we also don't have a complete list of modules which means that depending on the configuration of the instance which recieves the schedule request
                        //there may be modules which do not have their schedule satisified. to avoid this problem each instance should save a list of the modules it has loaded.
                        //which should be updated on-startup and when a new plugin is installed.
                        moduleBatchList.addAll(runBatch());
                        currentLocationRunDate = System.currentTimeMillis();
                    }
                } finally {
                    batchRunning.set(false);
                }
                if (!moduleBatchList.isEmpty() || !failedModuleBatchList.isEmpty()) {
                    LOG(Level.INFO, "[Scheduler] Requests for Batch Processing where dispatched successfully to: %s however dispatching failed for %s. "
                                    + "%s has elapsed since the last run of the schedule at this location", moduleBatchList.toString(), failedModuleBatchList.toString(),
                            previousLocationRunDate == null ? "[Scheduler Never Run]" : getTimeString(currentLocationRunDate - previousLocationRunDate));
                }
            }
        } else {
            JemoMessage result = pluginManager.process(msg);
            if (result != null) {
                //we should send the return message
                int sourceId = msg.getSourcePluginId();
                String sourceInstance = msg.getSourceInstance();
                int targetId = msg.getPluginId();
                msg.setSourceInstance(INSTANCE_QUEUE_URL);
                msg.setPluginId(sourceId);
                msg.setSourcePluginId(targetId);
                CloudProvider.getInstance().getRuntime().sendMessage(sourceInstance, toJSONString(msg));
            }
        }
    }

    public String getLOCATION() {
        return LOCATION;
    }

    public String getLOCATION_QUEUE_NAME() {
        return LOCATION_QUEUE_NAME;
    }

    public int getJEMO_HTTPS_PORT() {
        return JEMO_HTTPS_PORT;
    }

    public int getJEMO_HTTP_PORT() {
        return JEMO_HTTP_PORT;
    }

    public JemoHTTPConnector.MODE getJEMO_HTTP_MODE() {
        return JEMO_HTTP_MODE;
    }

    public Set<Integer> getPLUGIN_WHITELIST() {
        return PLUGIN_WHITELIST;
    }

    public Set<Integer> getPLUGIN_BLACKLIST() {
        return PLUGIN_BLACKLIST;
    }

    public long getQUEUE_POLL_WAIT_TIME() {
        return QUEUE_POLL_WAIT_TIME;
    }

    public boolean isIN_CLOUD_LOCATION() {
        return IN_CLOUD_LOCATION;
    }

    public JemoQueueListener sys_getInstanceQueueListener() {
        return instanceQueueListener;
    }

    public String getINSTANCE_ID() {
        return INSTANCE_ID;
    }

    public String getINSTANCE_QUEUE_URL() {
        return INSTANCE_QUEUE_URL;
    }

    public String getGLOBAL_QUEUE_URL() {
        return GLOBAL_QUEUE_URL;
    }

    public String getLOCATION_QUEUE_URL() {
        return LOCATION_QUEUE_URL;
    }

    public JemoPluginManager getPluginManager() {
        if (pluginManager == null) {
            synchronized (this) {
                pluginManager = new JemoPluginManager(this);
            }
        }
        return pluginManager;
    }

    public JemoHTTPConnector getHttpServer() {
        return httpServer;
    }

    public String getHOSTNAME() {
        if (HOSTNAME == null) {
            synchronized (this) {
                try {
                    HOSTNAME = java.net.InetAddress.getLocalHost().getHostName();
                } catch (Exception ex) {
                    NetworkInterface intf = Util.F(null, x -> Collections.list(NetworkInterface.getNetworkInterfaces()).stream().filter((n) -> {
                        try {
                            return !n.isLoopback();
                        } catch (SocketException ex1) {
                            return false;
                        }
                    }).findFirst().orElse(null));
                    if (intf != null) {
                        HOSTNAME = intf.getInetAddresses().nextElement().getHostAddress();
                    } else {
                        HOSTNAME = java.net.InetAddress.getLoopbackAddress().getHostName();
                    }
                }
            }
        }
        return HOSTNAME;
    }

    public ExecutorService getWORK_EXECUTOR() {
        return WORK_EXECUTOR;
    }

    public Logger getSYS_LOGGER() {
        return SYS_LOGGER;
    }

    public JemoLogFormatter getLOG_FORMATTER() {
        if (LOG_FORMATTER == null) {
            LOG_FORMATTER = new JemoLogFormatter(LOCATION, getHOSTNAME(), INSTANCE_ID);
        }
        return LOG_FORMATTER;
    }

    public Set<String> getMODULE_BATCH_BLACKLIST() {
        return MODULE_BATCH_BLACKLIST;
    }

    public ScheduledExecutorService getSCHEDULER() {
        return SCHEDULER;
    }

    public CopyOnWriteArrayList<JemoQueueListener> getQUEUE_LISTENERS() {
        return QUEUE_LISTENERS;
    }

    public long getLastSchedulerRun() {
        return lastSchedulerRun;
    }

    public long getLastBatchRunDate() {
        return lastBatchRunDate;
    }

    public AtomicBoolean getBatchRunning() {
        return batchRunning;
    }

    public long getLAST_SCHEDULER_POLL() {
        return LAST_SCHEDULER_POLL;
    }

    public void setINSTANCE_QUEUE_URL(String INSTANCE_QUEUE_URL) {
        this.INSTANCE_QUEUE_URL = INSTANCE_QUEUE_URL;
    }

    public boolean PLUGIN_VALID(int pluginId) {
        return (PLUGIN_WHITELIST.isEmpty() || PLUGIN_WHITELIST.contains(pluginId)) && (PLUGIN_BLACKLIST.isEmpty() || !PLUGIN_BLACKLIST.contains(pluginId));
    }

    public static boolean isInInstallationMode() {
        return IS_IN_INSTALLATION_MODE;
    }

    public static final String getTimeString(long elapsedTime) {
        return Util.getTimeString(elapsedTime);
    }

    public static final String toString(InputStream in) throws IOException {
        return Util.toString(in);
    }

    public static final <R extends Object, T extends Object> R executeFailsafe(ManagedFunctionWithException<T, R> func, T value) {
        try {
            return func.apply(value);
        } catch (Throwable ex) {
            return null;
        }
    }

    public static final <R extends Object, T extends Object> R executeFunction(ManagedFunction<T, R> func, T value) {
        return func.apply(value);
    }

    public static void stream(OutputStream out, InputStream in) throws IOException {
        Util.stream(out, in, true);
    }

    public static void stream(OutputStream out, InputStream in, boolean close) throws IOException {
        Util.stream(out, in, close);
    }

    private static <T> T jsonPathRead(String jsonString, String jsonPath) {
        return JsonPath.read(jsonString, jsonPath);
    }

    public static String getValueFromJSON(String jsonString, String jsonPath) {
        return jsonPathRead(jsonString, jsonPath);
    }

    public static List<String> getListFromJSON(String jsonString, String jsonPath) {
        return jsonPathRead(jsonString, jsonPath);
    }

    public static String md5(String str) throws NoSuchAlgorithmException {
        return Util.F(null, x -> Util.md5(str));
    }

    public static final boolean deleteDirectory(File dir) {
        return Util.deleteDirectory(dir);
    }

    public static final Class classOf(Object obj) {
        return obj.getClass();
    }

}
