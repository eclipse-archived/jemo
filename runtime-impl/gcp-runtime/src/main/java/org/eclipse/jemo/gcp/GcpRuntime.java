package org.eclipse.jemo.gcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.services.cloudresourcemanager.CloudResourceManager;
import com.google.api.services.iam.v1.Iam;
import com.google.api.services.iam.v1.IamScopes;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.MonitoredResource;
import com.google.cloud.ServiceOptions;
import com.google.cloud.datastore.*;
import com.google.cloud.datastore.StructuredQuery.PropertyFilter;
import com.google.cloud.logging.*;
import com.google.cloud.storage.*;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.Storage.BlobListOption;
import com.google.cloud.storage.Storage.BucketListOption;
import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.internal.model.*;
import org.eclipse.jemo.sys.ClusterParams;
import org.eclipse.jemo.sys.ClusterParams.ClusterParam;
import org.eclipse.jemo.sys.JemoRuntimeSetup;
import org.eclipse.jemo.sys.JemoRuntimeSetup.SetupParams;
import org.eclipse.jemo.sys.internal.TerraformJob;
import org.eclipse.jemo.sys.internal.TerraformJob.TerraformResult;
import org.eclipse.jemo.sys.internal.Util;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.google.api.client.googleapis.javanet.GoogleNetHttpTransport.newTrustedTransport;
import static com.google.api.client.json.jackson2.JacksonFactory.getDefaultInstance;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.Arrays.asList;
import static java.util.Collections.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.eclipse.jemo.sys.JemoRuntimeSetup.TFVARS_FILE_NAME;
import static org.eclipse.jemo.sys.internal.Util.*;

/**
 * Implementation of the GCP cloud runtime.
 *
 * @author Yannis Theocharis
 */
public class GcpRuntime implements CloudRuntime {

    private static final Logger LOG = Logger.getLogger(GcpRuntime.class.getSimpleName());
    private static final String GCP_PROJECT_ID = "project_id";
    private static final String GCP_SERVICE_ACCOUNT_ID = "service_account_id";
    static final String PROP_PROJECT_ID = "eclipse.jemo.gcp.project_id";
    static final String PROP_USER = "eclipse.jemo.gcp.user";
    private static final String QUEUES_METADATA = "queues_metadata";
    private static final String TABLES_METADATA = "tables_metadata";
    private static final String MODULE_CONFIGURATION_TABLE = "eclipse_jemo_module_configuration";
    private static final String ECLIPSE_JEMO_INSTANCES_TABLE = "eclipse_jemo_instances";
    private static String GCP_USER;
    private static String CLOUD_STORAGE_PLUGIN_BUCKET;

    private static final String APPLICATION_NAME = "Jemo";
    private static GoogleCredential CREDENTIALS;
    private static String PROJECT_ID;
    private static final ExecutorService EVENT_PROCESSOR = Executors.newCachedThreadPool();
    private static final String GCP_REGION_PROP = "ECLIPSE_JEMO_GCP_REGION";
    private static String REGION;

    private final Set<String> requiredRoles = new HashSet<String>() {{
        add("roles/datastore.user");
        add("roles/storage.admin");
        add("roles/logging.admin");
    }};
    private Iam iam_client;
    private Datastore datastore;
    private Storage storage;
    private Map<String, KeyFactory> kindToKeyFactory = new HashMap<>();
    private Map<String, Bucket> categoryToBucket = new HashMap<>();
    private Logging logging;
    private final AtomicBoolean LOGGING_INITIALIZED = new AtomicBoolean(false);

    public GcpRuntime() {
        Properties properties = readPropertiesFile();
        GCP_USER = System.getProperty(PROP_USER) != null ? System.getProperty(PROP_USER) : "jemo-user";
        PROJECT_ID = readProperty(PROP_PROJECT_ID, properties, ServiceOptions.getDefaultProjectId());
        REGION = readProperty(GCP_REGION_PROP, properties, null);
    }

    private Datastore datastore() {
        if (datastore == null) {
            GoogleCredentials credentials = credentials();
            datastore = DatastoreOptions.getDefaultInstance().toBuilder()
                    .setProjectId(PROJECT_ID)
                    .setCredentials(credentials)
                    .build()
                    .getService();
        }
        return datastore;
    }

    private GoogleCredentials credentials() {
        try {
            return GoogleCredentials.fromStream(new FileInputStream(jsonKeyFilePath(GCP_USER, PROJECT_ID).toFile()))
                    .createScoped(singleton(IamScopes.CLOUD_PLATFORM));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private GoogleCredential credential() {
        try {
            return GoogleCredential.fromStream(new FileInputStream(jsonKeyFilePath(GCP_USER, PROJECT_ID).toFile()))
                    .createScoped(singleton(IamScopes.CLOUD_PLATFORM));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Storage storage() {
        if (storage == null) {
            GoogleCredentials credentials = credentials();
            storage = StorageOptions.newBuilder()
                    .setCredentials(credentials)
                    .build().getService();
        }
        return storage;
    }

    private KeyFactory kindToKeyFactory(String kind) {
        return kindToKeyFactory.computeIfAbsent(kind, k -> datastore().newKeyFactory().setKind(k));
    }

    @Override
    public void start(AbstractJemo jemoServer) {
        CLOUD_STORAGE_PLUGIN_BUCKET = "jemopluginlib-" + PROJECT_ID;
        LOG.setUseParentHandlers(false);
        if (jemoServer !=null ) {
            LOG.addHandler(jemoServer.getConsoleHandler());
            LOG.setLevel(jemoServer.getConsoleHandler().getLevel());
        }
    }

    @Override
    public void storeModuleList(String moduleJar, List<String> moduleList) throws Throwable {
        final Bucket bucket = getOrCreateBucket(getDefaultCategory());
        bucket.create(moduleJar + ".modulelist", Jemo.toJSONString(moduleList).getBytes(UTF_8));
    }

    @Override
    public List<String> getModuleList(String moduleJar) throws Throwable {
        final Bucket bucket = getOrCreateBucket(getDefaultCategory());
        final String content = new String(bucket.get(moduleJar + ".modulelist").getContent(), UTF_8);
        return Jemo.fromJSONArray(String.class, content);
    }

    @Override
    public CloudBlob getModule(String moduleJar) throws IOException {
        final Bucket bucket = getOrCreateBucket(getDefaultCategory());
        final Blob blob = bucket.get(moduleJar);
        if (blob == null) {
            LOG(Level.INFO, "[%s][%s] the module could not be retrieved because it was not found in the plugin bucket %s", getClass().getSimpleName(), moduleJar, CLOUD_STORAGE_PLUGIN_BUCKET());
            return null;
        }
        return new CloudBlob(moduleJar, blob.getUpdateTime(), blob.getContent().length, new ByteArrayInputStream(blob.getContent()));
    }

    @Override
    public Long getModuleInstallDate(String moduleJar) throws IOException {
        final Bucket bucket = getOrCreateBucket(getDefaultCategory());
        final Blob blob = bucket.get(moduleJar + ".installed");
        return blob == null ? null : Long.valueOf(new String(blob.getContent(), UTF_8));
    }

    @Override
    public void setModuleInstallDate(String moduleJar, long installDate) throws IOException {
        final Bucket bucket = getOrCreateBucket(getDefaultCategory());
        bucket.create(moduleJar + ".installed", String.valueOf(installDate).getBytes(UTF_8));
    }

    private Logging logging() {
        if (LOGGING_INITIALIZED.compareAndSet(false, true)) {
            logging = LoggingOptions.newBuilder().setCredentials(credentials()).build().getService();
        }
        return logging;
    }

    @Override
    public void log(List<CloudLogEvent> eventList) {
        if (eventList == null) {
            return;
        }

        final Map<String, List<CloudLogEvent>> goupidToLogEvents = eventList.stream()
                .collect(Collectors.groupingBy((t) -> {
                    if (t.getModuleId() == -1) {
                        return "ECLIPSE-JEMO";
                    } else {
                        final String fileName = "ECLIPSE-JEMO_MODULE-" + t.getModuleId() + "-" + t.getModuleVersion() + "-" + t.getModuleName();
                        return fileName.replaceAll("[^\\.\\-_/#A-Za-z0-9]", "");
                    }
                }));

        goupidToLogEvents.forEach((k, v) -> {
            final HashMap<String, String> labels = new HashMap<String, String>() {{
                put("project_id", PROJECT_ID);
                put("name", k);
            }};
            final Set<LogEntry> logEntries = v.parallelStream()
                    .map(event -> LogEntry.newBuilder(Payload.StringPayload.of(event.getMessage()))
                            .setSeverity(toGCPSeverity(event.getLevel()))
                            .setLogName(k)
                            .setResource(MonitoredResource.newBuilder("logging_log").setLabels(labels).build())
                            .setTimestamp(event.getTimestamp()).build())
                    .collect(toSet());
            logging().write(logEntries);
        });
    }

    private Severity toGCPSeverity(String level) {
        if (level == null) {
            return Severity.INFO;
        }

        switch (level) {
            case "SEVERE":
                return Severity.ERROR;
            case "WARNING":
                return Severity.WARNING;
            case "INFO":
                return Severity.INFO;
            case "CONFIG":
                return Severity.DEBUG;
            case "FINE":
                return Severity.DEBUG;
            case "FINER":
                return Severity.DEBUG;
            case "FINEST":
                return Severity.DEBUG;
            default:
                return Severity.DEFAULT;
        }
    }

    @Override
    public Set<String> listPlugins() {
        Set<String> plugins = new HashSet<>();
        final Bucket bucket = getOrCreateBucket(getDefaultCategory());
        bucket.list().iterateAll().forEach(blob -> {
            if (blob.getName().endsWith(".jar")) {
                plugins.add(blob.getName());
            }
        });
        return plugins;
    }

    @Override
    public void uploadModule(String pluginFile, byte[] pluginBytes) {
        final Bucket bucket = getOrCreateBucket(getDefaultCategory());
        bucket.create(pluginFile, pluginBytes);
    }

    @Override
    public void uploadModule(String pluginFile, InputStream in, long moduleSize) {
        final Bucket bucket = getOrCreateBucket(getDefaultCategory());
        bucket.create(pluginFile, in);
    }

    @Override
    public String defineQueue(String queueName) {
        saveNoSQL(QUEUES_METADATA, new SystemDBMetaData(queueName));
        return queueName;
    }

    @Override
    public String getQueueId(String queueName) {
        try {
            final SystemDBMetaData queuesMetaData = getNoSQL(QUEUES_METADATA, queueName, SystemDBMetaData.class);
            return queuesMetaData == null ? null : queuesMetaData.getId();
        } catch (IOException e) {
            return null;
        }
    }

    @Override
    public String getQueueName(String queueId) {
        return queueId;
    }

    @Override
    public List<String> listQueueIds(String location, boolean includeWorkQueues) {
        final String prefix = "JEMO-" + (location == null ? "" : location);

        return listNoSQL(QUEUES_METADATA, SystemDBMetaData.class).stream()
                .filter(systemDBMetaData -> {
                    final String id = systemDBMetaData.getId();
                    final int index = id.lastIndexOf('/');
                    final String name = id.substring(index + 1);
                    return name.startsWith(prefix) &&
                            (!name.endsWith("-WORK-QUEUE") || includeWorkQueues);
                })
                .map(SystemDBMetaData::getId)
                .collect(toList());
    }

    @Override
    public int pollQueue(String queueId, CloudQueueProcessor processor) throws QueueDoesNotExistException {
        try {
            final SystemDBMetaData systemDBMetaData = getNoSQL(QUEUES_METADATA, queueId, SystemDBMetaData.class);
            if (systemDBMetaData == null) {
                throw new QueueDoesNotExistException("Queue id: " + queueId);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        int[] messageNumber = {0};
        final List<SystemDBMessage> systemDBMessages = listNoSQL(queueId, SystemDBMessage.class);
        systemDBMessages.stream()
                .limit(10).parallel()
                .forEach(msg -> {
                    messageNumber[0]++;
                    //first we delete so nobody else will get this message.
                    deleteNoSQL(queueId, msg);
                    JemoMessage ccMsg = Util.F(msg, (SystemDBMessage x) -> Jemo.fromJSONString(JemoMessage.class, x.getJsonMessage()));

                    //the message will contain a relevant plugin-id so we will use that id to know what we need to run.
                    if (ccMsg != null) {
                        EVENT_PROCESSOR.submit(() -> processor.processMessage(ccMsg));
                    }
                });
        return messageNumber[0];
    }

    @Override
    public void deleteQueue(String queueId) {
        dropTable(QUEUES_METADATA, queueId);
    }

    @Override
    public String sendMessage(String queueId, String jsonMessage) {
        // Google PubSub service only delivers messages to active subscriptions.
        // This means that a Jemo instance will miss all the messages sent before it has created its subscription.
        // This is undesireable behaviour as Jemo instances can go down and restart again and we don't want to miss the messages send inbetween.
        // Therefore, we save the message to datastore, under a kind named with queueId.
        final SystemDBMessage systemDBMessage = new SystemDBMessage(jsonMessage);
        saveNoSQL(queueId, systemDBMessage);
        return systemDBMessage.getId();
    }

    @Override
    public void createNoSQLTable(String tableName) {
        // It is not possible to create an empty "Kind" (similar to teh relational "Table" or the document db "Collection" term) GCP datastore.
        // The "Kind" is automatically created the first time an "Entity" (similar to "Row" or "Document") is added under the Kind.
        // Similarly, the "Kind" is deleted when the last "Entity" under it is deleted.
        // Therefore, we use a separate metadata table to store the names of "existing" tables.
        saveNoSQL(TABLES_METADATA, new SystemDBMetaData(tableName));
    }

    @Override
    public boolean hasNoSQLTable(String tableName) {
        final KeyFactory keyFactory = kindToKeyFactory(TABLES_METADATA);
        final Entity entity = datastore().get(keyFactory.newKey(tableName));
        return entity != null;
    }

    @Override
    public void dropNoSQLTable(String tableName) {
        // Datastore does not provide a way to delete a collection.
        // Instead, we need to list all the documents under the collection and delete each one of them.
        dropTable(TABLES_METADATA, tableName);
    }

    public void dropTable(String metadataTable, String tableName) {
        final Query<Entity> query = Query.newEntityQueryBuilder().setKind(tableName).build();
        List<Key> keys = new ArrayList<>();
        datastore().run(query).forEachRemaining(entity -> keys.add(entity.getKey()));
        datastore().delete(keys.toArray(new Key[0]));
        kindToKeyFactory.remove(tableName);
        deleteNoSQL(metadataTable, new SystemDBMetaData(tableName));
    }

    @Override
    public <T> List<T> listNoSQL(String tableName, Class<T> objectType) {
        List<T> retval = new ArrayList<>();
        try {
            final Query<Entity> query = Query.newEntityQueryBuilder().setKind(tableName).build();
            datastore().run(query).forEachRemaining(entity -> retval.add(deserialiseEntity(objectType, entity)));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return retval;
    }

    private <T> T deserialiseEntity(Class<T> objectType, Entity entity) {
        final String dataAsString = entity.getString("data");
        return Util.F(dataAsString, data -> Jemo.fromJSONString(objectType, data));
    }

    @Override
    public <T> List<T> queryNoSQL(String tableName, Class<T> objectType, String... pkList) {
        final KeyFactory keyFactory = kindToKeyFactory(tableName);
        final Key[] keys = Arrays.stream(pkList)
                .map(keyFactory::newKey)
                .toArray(Key[]::new);
        List<T> result = new ArrayList<>();
        datastore().get(keys).forEachRemaining(entity -> result.add(deserialiseEntity(objectType, entity)));
        return result;
    }

    @Override
    public <T> T getNoSQL(String tableName, String id, Class<T> objectType) throws IOException {
        if (!tableName.equals(QUEUES_METADATA) && !tableName.equals(TABLES_METADATA) && !hasNoSQLTable(tableName)) {
            throw new IOException("Table does not exist, tableName: " + tableName);
        }

        final KeyFactory keyFactory = kindToKeyFactory(tableName);
        final Entity entity = datastore().get(keyFactory.newKey(id));
        if (entity == null) {
            return null;
        }

        final String dataAsString = entity.getString("data");
        try {
            return Jemo.fromJSONString(objectType, dataAsString);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveNoSQL(String tableName, SystemDBObject... data) {
        final KeyFactory keyFactory = kindToKeyFactory(tableName);
        final Entity[] entities = Arrays.stream(data)
                .map(datum -> {
                    StringValue myStringValue = StringValue.newBuilder(Jemo._safe_toJSONString(datum))
                            .setExcludeFromIndexes(true)
                            .build();
                    return Entity.newBuilder(keyFactory.newKey(datum.getId()))
                            .set("data", myStringValue)
                            .build();
                })
                .toArray(Entity[]::new);
        datastore().put(entities);
    }

    @Override
    public void deleteNoSQL(String tableName, SystemDBObject... data) {
        final KeyFactory keyFactory = kindToKeyFactory(tableName);
        final Key[] keys = Arrays.stream(data)
                .map(datum -> keyFactory.newKey(datum.getId()))
                .toArray(Key[]::new);
        datastore().delete(keys);
    }

    @Override
    public void watchdog(String location, String instanceId, String instanceQueueUrl) {
        if (!hasNoSQLTable(instanceQueueUrl)) {
            createNoSQLTable(ECLIPSE_JEMO_INSTANCES_TABLE);
        }

        final KeyFactory keyFactory = kindToKeyFactory(ECLIPSE_JEMO_INSTANCES_TABLE);
        final Entity entity = Entity.newBuilder(keyFactory.newKey(instanceId))
                .set("instance_id", instanceId)
                .set("last_seen", System.currentTimeMillis())
                .set("instance_location", location)
                .set("instance_url", instanceQueueUrl)
                .build();
        datastore().put(entity);

        final long inactivityPeriodThreshold = System.currentTimeMillis() - 7_200_000;
        final Query<Entity> query = Query.newEntityQueryBuilder()
                .setKind(instanceQueueUrl)
                .setFilter(PropertyFilter.lt("last_seen", inactivityPeriodThreshold))
                .build();
        datastore().run(query).forEachRemaining(inactiveQueue -> dropNoSQLTable(inactiveQueue.getString("instance_url")));
    }

    @Override
    public void setModuleConfiguration(int pluginId, ModuleConfiguration config) {
        provisionConfigTable();
        if (config != null && !config.getParameters().isEmpty()) {
            final KeyFactory keyFactory = kindToKeyFactory(MODULE_CONFIGURATION_TABLE);
            List<Key> keys = new ArrayList<>();
            List<Entity> entitiesToUpdate = new ArrayList<>();
            config.getParameters().forEach(p -> {
                switch (p.getOperation()) {
                    case delete:
                        keys.add(keyFactory.newKey(pluginId + "_" + p.getKey()));
                        break;
                    case upsert:
                        entitiesToUpdate.add(Entity.newBuilder(keyFactory.newKey(pluginId + "_" + p.getKey()))
                                .set("key", p.getKey())
                                .set("value", p.getValue())
                                .build());
                        break;
                }
            });
            datastore().delete(keys.toArray(new Key[0]));
            datastore().put(entitiesToUpdate.toArray(new Entity[0]));
        }
    }

    @Override
    public Map<String, String> getModuleConfiguration(int pluginId) {
        provisionConfigTable();
        final Map<String, String> config = new HashMap<>();
        Query<Entity> query = Query.newEntityQueryBuilder().setKind(MODULE_CONFIGURATION_TABLE).build();
        datastore().run(query).forEachRemaining(entity -> {
            if (entity.getKey().getName().startsWith(String.valueOf(pluginId))) {
                config.put(entity.getString("key"), entity.getString("value"));
            }
        });
        return config;
    }

    private void provisionConfigTable() {
        if (!hasNoSQLTable(MODULE_CONFIGURATION_TABLE)) {
            createNoSQLTable(MODULE_CONFIGURATION_TABLE);
        }
    }

    @Override
    public void store(String key, Object data) {
        store(CLOUD_STORAGE_PLUGIN_BUCKET(), key, data);
    }

    private String CLOUD_STORAGE_PLUGIN_BUCKET() {
        return CLOUD_STORAGE_PLUGIN_BUCKET;
    }

    @Override
    public <T> T retrieve(String key, Class<T> objType) {
        return retrieve(CLOUD_STORAGE_PLUGIN_BUCKET(), key, objType);
    }

    @Override
    public void store(String category, String key, Object data) {
        final Bucket bucket = getOrCreateBucket(category);
        try {
            final byte[] byteData = Jemo.toJSONString(data).getBytes(UTF_8);
            bucket.create(Jemo.md5(key) + ".datastore", byteData, "application/json");
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private Bucket getOrCreateBucket(String category) {
        return categoryToBucket.computeIfAbsent(category, key -> {
            final String bucketName = buildBucketName(category);
            final Iterator<Bucket> buckets = storage().list(BucketListOption.pageSize(1), BucketListOption.prefix(bucketName)).iterateAll().iterator();
            return buckets.hasNext() ? buckets.next() : storage().create(
                    BucketInfo.newBuilder(bucketName)
                            // See here for possible values: http://g.co/cloud/storage/docs/storage-classes
                            .setStorageClass(StorageClass.REGIONAL)
                            // Possible values: http://g.co/cloud/storage/docs/bucket-locations#location-mr
                            .setLocation(REGION.substring(0, REGION.lastIndexOf('-')))
                            .build());
        });
    }

    private String buildBucketName(String name) {
        final String suffix = name.endsWith(PROJECT_ID) ? "" : "-" + PROJECT_ID;

        //all bucket names must be relevant to the current account otherwise there will be conflicts
        return name.toLowerCase().replaceAll("[\\_\\.]", "-") + suffix;
    }

    @Override
    public <T> T retrieve(String category, String key, Class<T> objType) {
        final Bucket bucket = getOrCreateBucket(category);
        try {
            final Blob blob = bucket.get(Jemo.md5(key) + ".datastore");
            return blob == null ? null : Jemo.fromJSONString(objType, new String(blob.getContent()));
        } catch (IOException | StorageException e) {
            return null;
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void delete(String category, String key) {
        final String bucketName = buildBucketName(category);
        try {
            final BlobId blobId = BlobId.of(bucketName, Jemo.md5(key) + ".datastore");
            storage().delete(blobId);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public String getDefaultCategory() {
        return CLOUD_STORAGE_PLUGIN_BUCKET();
    }

    @Override
    public void write(String category, String path, String key, InputStream dataStream) {
        final Bucket bucket = getOrCreateBucket(category);
        bucket.create(path + "/" + key, dataStream);
    }

    @Override
    public InputStream read(String category, String path, String key) {
        final Bucket bucket = getOrCreateBucket(category);
        final Blob blob = bucket.get(path + "/" + key);
        if (blob == null) {
            return null;
        }

        try {
            return new ByteArrayInputStream(blob.getContent());
        } catch (StorageException e) {
            return null;
        }
    }

    @Override
    public Stream<InputStream> readAll(String category, String path) {
        final Bucket bucket = getOrCreateBucket(category);
        return StreamSupport.stream(bucket.list(BlobListOption.prefix(path + "/")).iterateAll().spliterator(), true)
                .map(blob -> (InputStream) new ByteArrayInputStream(blob.getContent()))
                .parallel();
    }

    @Override
    public void remove(String category, String path, String key) {
        final String bucketName = buildBucketName(category);
        final BlobId blobId = BlobId.of(bucketName, path == null ? key : path + "/" + key);
        storage().delete(blobId);
    }

    @Override
    public ValidationResult validatePermissions() {
        try {
            if (iam_client == null) {
                ValidationResult validationResult = validateCredentials(credential());
                if (!validationResult.isSuccess()) {
                    return validationResult;
                }
            }

            final CloudResourceManager cloudResourceManager = new CloudResourceManager.Builder(newTrustedTransport(), getDefaultInstance(), CREDENTIALS)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            final List<com.google.api.services.cloudresourcemanager.model.Binding> bindings = cloudResourceManager.projects().getIamPolicy(CREDENTIALS.getServiceAccountProjectId(), null).execute().getBindings();
            final String jemoServiceAccount = "serviceAccount:" + CREDENTIALS.getServiceAccountId();
            final Set<String> grantedRoles = bindings.stream()
                    .filter(binding -> binding.getMembers().contains(jemoServiceAccount))
                    .map(binding -> binding.getRole())
                    .collect(toSet());
            if (grantedRoles.contains("roles/owner")) {
                return ValidationResult.SUCCESS;
            }

            final List<String> notGrantedRoles = requiredRoles.stream()
                    .filter(requiredPermission -> !grantedRoles.contains(requiredPermission))
                    .collect(toList());

            ValidationResult validationResult = new ValidationResult(notGrantedRoles);
            if (!validationResult.isSuccess()) {
                LOG(Level.WARNING, String.format("The following user permissions are missing: [%s]", notGrantedRoles));
            }
            return validationResult;
        } catch (Exception e) {
            return new ValidationResult(singletonList(e.getMessage()));
        }
    }

    @Override
    public ValidationResult validateCredentials(Map<String, String> credentials) {
        final String projectId = credentials.get(GCP_PROJECT_ID);
        final String serviceAccountName = credentials.get(GCP_SERVICE_ACCOUNT_ID);

        try {
            final Path credentialsFilePath = jsonKeyFilePath(serviceAccountName, projectId);
            final GoogleCredential credential = GoogleCredential
                    .fromStream(new FileInputStream(credentialsFilePath.toFile()))
                    .createScoped(singleton(IamScopes.CLOUD_PLATFORM));
            final ValidationResult validationResult = validateCredentials(credential);
            if (validationResult.isSuccess()) {
                PROJECT_ID = projectId;
                // Also add the jemo role to the required roles, as this requires knowing the PROJECT_ID.
                requiredRoles.add("projects/" + PROJECT_ID + "/roles/jemoRole");
            }
            return validationResult;
        } catch (IOException e) {
            LOG.log(Level.FINE, String.format("User credentials validation failed: [%s].", e.getMessage()));
            return new ValidationResult(singletonList(e.getMessage()));
        }
    }

    public ValidationResult validateCredentials(GoogleCredential credential) {
        try {
            final Iam iamClient = new Iam.Builder(newTrustedTransport(), getDefaultInstance(), credential)
                    .setApplicationName(APPLICATION_NAME)
                    .build();

            iamClient.projects().serviceAccounts().get("projects/" + credential.getServiceAccountProjectId() + "/serviceAccounts/" + credential.getServiceAccountId()).execute();
            iam_client = iamClient;
            CREDENTIALS = credential;
            return ValidationResult.SUCCESS;
        } catch (GeneralSecurityException | IOException e) {
            return new ValidationResult(singletonList("Credentials validation failed. GCP responded with: \n\n" + e.getMessage()));
        }
    }

    @Override
    public void updateCredentials(Map<String, String> credentials) {
        // Do nothing.
    }

    Path jsonKeyFilePath(String userAccountId, String projectId) {
        if (System.getenv("GOOGLE_APPLICATION_CREDENTIALS") != null) {
            return Paths.get(System.getenv("GOOGLE_APPLICATION_CREDENTIALS"));
        } else {
            return gcpDirectory().resolve(jsonKeyFileName(userAccountId, projectId));
        }
    }

    private String jsonKeyFileName(String userAccountId, String projectId) {
        return userAccountId + "@" + projectId + "-cred.json";
    }

    @Override
    public void setRegion(String regionCode) {
        REGION = regionCode;
        addJemoProperty(GCP_REGION_PROP, regionCode);
    }

    @Override
    public String readInstanceTag(String key) {
        return null;
    }

    @Override
    public List<RegionInfo> getRegions() {
        return asList(new RegionInfo("us-east1-b", "South Carolina-b"),
                new RegionInfo("us-east1-c", "South Carolina-c"),
                new RegionInfo("us-east1-d", "South Carolina-d"),
                new RegionInfo("us-east4-a", "N. Virginia-a"),
                new RegionInfo("us-east4-b", "N. Virginia-b"),
                new RegionInfo("us-east4-c", "N. Virginia-c"),
                new RegionInfo("us-central1-a", "Iowa-a"),
                new RegionInfo("us-central1-b", "Iowa-b"),
                new RegionInfo("us-central1-c", "Iowa-c"),
                new RegionInfo("us-central1-f", "Iowa-f"),
                new RegionInfo("us-west1-a", "Oregon-a"),
                new RegionInfo("us-west1-b", "Oregon-b"),
                new RegionInfo("us-west1-c", "Oregon-c"),
                new RegionInfo("europe-west4-a", "Netherlands-a"),
                new RegionInfo("europe-west4-b", "Netherlands-b"),
                new RegionInfo("europe-west4-c", "Netherlands-c"),
                new RegionInfo("europe-west1-b", "Belgium-b"),
                new RegionInfo("europe-west1-c", "Belgium-c"),
                new RegionInfo("europe-west1-d", "Belgium-d"),
                new RegionInfo("europe-west3-a", "Frankfurt-a"),
                new RegionInfo("europe-west3-b", "Frankfurt-b"),
                new RegionInfo("europe-west3-c", "Frankfurt-c"),
                new RegionInfo("europe-west2-a", "London-a"),
                new RegionInfo("europe-west2-b", "London-b"),
                new RegionInfo("europe-west2-c", "London-c"),
                new RegionInfo("asia-east1-a", "Taiwan-a"),
                new RegionInfo("asia-east1-b", "Taiwan-b"),
                new RegionInfo("asia-east1-c", "Taiwan-c"),
                new RegionInfo("asia-southeast1-a", "Singapore-a"),
                new RegionInfo("asia-southeast1-b", "Singapore-b"),
                new RegionInfo("asia-southeast1-c", "Singapore-c"),
                new RegionInfo("asia-northeast1-a", "Tokyo-a"),
                new RegionInfo("asia-northeast1-b", "Tokyo-b"),
                new RegionInfo("asia-northeast1-c", "Tokyo-c"),
                new RegionInfo("asia-south1-a", "Mumbai-a"),
                new RegionInfo("asia-south1-b", "Mumbai-b"),
                new RegionInfo("asia-south1-c", "Mumbai-c"),
                new RegionInfo("australia-southeast1-a", "Sydney-a"),
                new RegionInfo("australia-southeast1-b", "Sydney-b"),
                new RegionInfo("australia-southeast1-c", "Sydney-c"),
                new RegionInfo("southamerica-east1-a", "Sao Paulo-a"),
                new RegionInfo("southamerica-east1-b", "Sao Paulo-b"),
                new RegionInfo("southamerica-east1-c", "Sao Paulo-c"),
                new RegionInfo("asia-east2-a", "Hong Kong-a"),
                new RegionInfo("asia-east2-b", "Hong Kong-b"),
                new RegionInfo("asia-east2-c", "Hong Kong-c"),
                new RegionInfo("asia-northeast2-a", "Osaka-a"),
                new RegionInfo("asia-northeast2-b", "Osaka-b"),
                new RegionInfo("asia-northeast2-c", "Osaka-c"),
                new RegionInfo("europe-north1-a", "Finland-a"),
                new RegionInfo("europe-north1-b", "Finland-b"),
                new RegionInfo("europe-north1-c", "Finland-c"),
                new RegionInfo("europe-west6-a", "Zurich-a"),
                new RegionInfo("europe-west6-b", "Zurich-b"),
                new RegionInfo("europe-west6-c", "Zurich-c"),
                new RegionInfo("northamerica-northeast1-a", "Montreal-a"),
                new RegionInfo("northamerica-northeast1-b", "Montreal-b"),
                new RegionInfo("northamerica-northeast1-c", "Montreal-c"),
                new RegionInfo("us-west2-a", "Los Angeles-a"),
                new RegionInfo("us-west2-b", "Los Angeles-b"),
                new RegionInfo("us-west2-c", "Los Angeles-c"));
    }

    @Override
    public void resetLogConsoleHandler(Handler handler) {
        LOG.removeHandler(LOG.getHandlers()[0]);
        LOG.addHandler(handler);
        LOG.setLevel(handler.getLevel());
    }

    @Override
    public List<String> getExistingNetworks() {
        return null;
    }

    @Override
    public List<String> getCustomerManagedPolicies() {
        return null;
    }

    @Override
    public ValidationResult validatePolicy(String policyName) {
        return null;
    }

    @Override
    public JemoRuntimeSetup.ClusterCreationResponse createCluster(SetupParams setupParams, StringBuilder builder) throws IOException {
        final Path terraformDirPath = createClusterTerraformTemplates(setupParams);
        final TerraformJob terraformJob = new TerraformJob(terraformDirPath.toString(), terraformDirPath.toString() + "/" + TFVARS_FILE_NAME).run(builder);
        final Path source = Paths.get("terraform.tfstate");
        if (Files.exists(source)) {
            Files.copy(source, terraformDirPath.resolve("terraform.tfstate"));
        }

        final String kubernetesDir = getTerraformClusterDir() + "kubernetes/";
        runProcess(builder, new String[]{
                "/bin/sh", "-c", "gcloud container clusters get-credentials jemo-cluster ; " +
                "kubectl create -f " + kubernetesDir + "/credentials.yaml ; " +
                "kubectl create -f " + kubernetesDir + "/jemo-statefulset.yaml ; " +
                "kubectl rollout status statefulset jemo ;" +
                "kubectl create -f " + kubernetesDir + "/jemo-svc.yaml"
        });

        final String loadBalancerUrl = loadBalancerUrl(builder);
        return JemoRuntimeSetup.ClusterCreationResponse.fromTerraformJob(terraformJob).setLoadBalancerUrl(loadBalancerUrl);
    }

    private String loadBalancerUrl(StringBuilder builder) throws IOException {
        long start = System.currentTimeMillis();
        long duration;
        duration = (System.currentTimeMillis() - start) / 60_000;

        String[] result;
        do {
            Util.B(null, x -> TimeUnit.SECONDS.sleep(10));
            result = runProcess(builder, new String[]{
                    "/bin/sh", "-c", "kubectl get svc jemo -o=jsonpath='{.status.loadBalancer.ingress[0].ip}'"
            });
        } while (duration < 3 && result[0].isEmpty());

        return "http://" + result[0];
    }

    @Override
    public Path createClusterTerraformTemplates(SetupParams setupParams) throws IOException {
        prepareClusterCreation(setupParams);
        final Path terraformDirPath = prepareTerraformFiles(setupParams);

        final Path kubernetesDirPath = terraformDirPath.resolve("kubernetes");
        if (!Files.exists(kubernetesDirPath)) {
            Files.createDirectory(kubernetesDirPath);
        }
        final String sourceDir = getTerraformClusterDir() + "kubernetes/";
        copy(sourceDir, kubernetesDirPath, "jemo-svc.yaml", getClass());

        final String jemoKeyFileContent = Files.lines(jsonKeyFilePath(GCP_USER, PROJECT_ID)).collect(Collectors.joining("\n"));
        final String encodedJemoKeyFileContent = Base64.getEncoder().encodeToString(jemoKeyFileContent.getBytes(UTF8_CHARSET));
        applyTemplate(sourceDir, kubernetesDirPath, "credentials.yaml", getClass(), x -> x.replaceAll("_JEMO_USER_CRED_", encodedJemoKeyFileContent));

        final String replicas = setupParams.parameters().get("gcp_cluster_count");
        applyTemplate(sourceDir, kubernetesDirPath, "jemo-statefulset.yaml", getClass(), x -> x.replaceAll("_JEMO_REPLICAS_", replicas).replaceAll("_REGION_", REGION));

        return terraformDirPath;
    }

    private Path prepareTerraformFiles(JemoRuntimeSetup.SetupParams setupParams) throws IOException {
        final String terraformDir = getTerraformClusterDir();
        final Path terraformDirPath = Paths.get(terraformDir);
        if (Files.exists(terraformDirPath)) {
            Util.deleteDirectory(terraformDirPath.toFile());
        }
        Files.createDirectories(terraformDirPath);

        copy(terraformDir, terraformDirPath, "README.txt", getClass());
        copy(terraformDir, terraformDirPath, "variables.tf", getClass());
        copy(terraformDir, terraformDirPath, "cluster.tf", getClass());

        setupParams.parameters().put("region", REGION);
        setupParams.parameters().put("project_id", PROJECT_ID);
        setupParams.parameters().put("credentials_file", jsonKeyFilePath("terraform-user", PROJECT_ID).toString());
        final String varsFileContent = setupParams.parameters().keySet().stream()
                .filter(key -> !key.equals("containersPerParamSet"))
                .map(key -> key + "=\"" + setupParams.parameters().get(key) + "\"")
                .collect(Collectors.joining("\n"));

        Files.write(Paths.get(terraformDirPath.toString() + "/" + JemoRuntimeSetup.TFVARS_FILE_NAME), varsFileContent.getBytes(), CREATE);
        return terraformDirPath;
    }

    @Override
    public List<InstallProperty> getInstallProperties() {
        return null;
    }

    @Override
    public void setInstallProperties(Map<String, String> properties) {
        final String userAccountId = properties.get("user_account_id");
        final Path jsonKeyFilePath = jsonKeyFilePath(userAccountId, PROJECT_ID);
        Util.B(jsonKeyFilePath, path -> {
            Files.deleteIfExists(path);
            TimeUnit.SECONDS.sleep(1);
        });

        Util.B(jsonKeyFilePath, (path) -> Files.copy(Paths.get(getTerraformInstallDir()).resolve(path.getFileName().toString()), path, REPLACE_EXISTING));
        addJemoProperty(PROP_PROJECT_ID, PROJECT_ID);
    }

    @Override
    public JemoRuntimeSetup.TerraformJobResponse install(String region, StringBuilder builder) throws IOException {
        final Path terraformDirPath = createInstallTerraformTemplates(region);

        // Enabling GCP APIs with terraform take some time to take effect on GCP. The APIs appear as enabled on the GCP console
        // but if we send request immediately, they fail with a message that the API is not enabled. This behaviour lasts for 20-30 secs after the service is enabled,
        // Therefore, I wait for a minute after terraform has finished.
        final TerraformJob terraformJob = new TerraformJob(terraformDirPath.toString(), terraformDirPath.toString() + "/" + JemoRuntimeSetup.TFVARS_FILE_NAME)
                .withDelay(TimeUnit.MINUTES, 1)
                .run(builder);
        Files.copy(Paths.get("terraform.tfstate"), terraformDirPath.resolve("terraform.tfstate"));
        return JemoRuntimeSetup.TerraformJobResponse.fromTerraformJob(terraformJob);
    }

    @Override
    public Path createInstallTerraformTemplates(String region) throws IOException {
        final String terraformDir = getTerraformInstallDir();
        final Path terraformDirPath = Paths.get(terraformDir);
        if (Files.exists(terraformDirPath)) {
            // Delete files from previous runs.
            Util.deleteDirectory(terraformDirPath.toFile());
        }
        Files.createDirectories(terraformDirPath);

        copy(terraformDir, terraformDirPath, "install.tf", getClass());
        copy(terraformDir, terraformDirPath, "variables.tf", getClass());
        if (region != null) {
            final String tfvarsFileContent = "project_id=\"" + PROJECT_ID + "\"\n" +
                    "region=\"" + region + "\"\n" +
                    "credentials_file=\"" + jsonKeyFilePath("terraform-user", PROJECT_ID) + "\"\n";
            Files.write(Paths.get(terraformDirPath.toString() + "/" + JemoRuntimeSetup.TFVARS_FILE_NAME), tfvarsFileContent.getBytes(), CREATE);
        }
        return terraformDirPath;
    }

    @Override
    public Map<String, String> getCredentialsFromTerraformResult(TerraformResult terraformResult) {
        return null;
    }

    @Override
    public AdminUserCreationInstructions getAdminUserCreationInstructions() {
        return new AdminUserCreationInstructions("Jemo setup requires a GCP service account with the \"Owner\" role to run terraform with.",
                asList("Create a service account with the \"terraform-user\" name: \ngcloud iam service-accounts create terraform-user",
                        "Attach the \"Owner\" role to terraform-user (replace PROJECT_ID with your project id): \ngcloud projects add-iam-policy-binding [PROJECT_ID] --member \"serviceAccount:terraform-user@[PROJECT_ID].iam.gserviceaccount.com\" --role \"roles/owner\"",
                        "Create a json key file to be used by terraform to retrieve the credentials: \ngcloud iam service-accounts keys create terraform-user@[PROJECT_ID]-cred.json --iam-account terraform-user@[PROJECT_ID].iam.gserviceaccount.com",
                        "Create a directory \"~/.gcp\" and copy the json key file there: \nmkdir ~/.gcp; \ncp terraform-user@[PROJECT_ID]-cred.json ~/.gcp/"
                ));
    }

    @Override
    public ClusterParams getClusterParameters() {
        return new ClusterParams(
                singletonList(new ClusterParam("cluster_name", "jemo-cluster", "the cluster name")),
                singletonList(new ClusterParam("gcp_cluster_count", "2", "the number of nodes")),
                emptyList()
        );
    }

    @Override
    public void deleteKubernetesResources(StringBuilder builder) throws IOException {
        // Do nothing, "terraform destroy" succeeds on GCP even if kubernetes pods and services are running.
    }

    @Override
    public String getCspLabel() {
        return "gcp";
    }

    @Override
    public void removePluginFiles(String pluginJarFileName) {
        remove(null, pluginJarFileName);
        remove(null, pluginJarFileName + ".installed");
        remove(null, pluginJarFileName + ".modulelist");
        delete(getDefaultCategory(), pluginJarFileName + ".classlist");
        delete(getDefaultCategory(), pluginJarFileName + ".crc32");
    }

    @Override
    public String isCliInstalled() {
        final boolean isInstalled = isCommandInstalled("gcloud --help");
        return isInstalled ? null : "Please install 'gcloud'. Instructions on https://cloud.google.com/sdk/install";
    }

    private static Path gcpDirectory() {
        final Path gcpDirPath = Util.pathUnderHomdeDir(".gcp");
        if (!Files.exists(gcpDirPath)) {
            Util.B(gcpDirPath, path -> Files.createDirectory(path));
        }
        return gcpDirPath;
    }

    private static final void LOG(Level logLevel, String message, Object... args) {
        LOG.log(logLevel, message, args);
    }

    private static class SystemDBMessage implements SystemDBObject {

        private String id;
        private String jsonMessage;

        public SystemDBMessage() {
        }

        public SystemDBMessage(String jsonMessage) {
            id = UUID.randomUUID().toString();
            this.jsonMessage = jsonMessage;
        }

        @Override
        public String getId() {
            return id;
        }

        public String getJsonMessage() {
            return jsonMessage;
        }
    }

    private static class SystemDBMetaData implements SystemDBObject {

        private String id;

        public SystemDBMetaData() {
        }

        public SystemDBMetaData(String id) {
            this.id = id;
        }

        @Override
        public String getId() {
            return id;
        }

    }
}
