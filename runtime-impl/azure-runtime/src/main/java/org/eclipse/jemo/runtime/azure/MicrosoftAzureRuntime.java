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
package org.eclipse.jemo.runtime.azure;

import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.internal.model.*;

import static org.eclipse.jemo.AbstractJemo.getListFromJSON;
import static org.eclipse.jemo.Jemo.fromJSONString;
import static org.eclipse.jemo.Jemo.getValueFromJSON;
import static org.eclipse.jemo.Jemo.executeFunction;
import static org.eclipse.jemo.sys.JemoRuntimeSetup.TFVARS_FILE_NAME;
import static org.eclipse.jemo.sys.internal.Util.*;
import static org.eclipse.jemo.runtime.azure.MicrosoftAzureRuntime.HttpMode.GET;
import static org.eclipse.jemo.runtime.azure.MicrosoftAzureRuntime.MESSAGE_MODEL.EVENTHUB;
import static org.eclipse.jemo.runtime.azure.MicrosoftAzureRuntime.MESSAGE_MODEL.QUEUE;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.Arrays.asList;
import static java.util.stream.Collectors.toList;

import org.eclipse.jemo.internal.model.CloudBlob;
import org.eclipse.jemo.sys.JemoPluginManager;
import org.eclipse.jemo.sys.JemoRuntimeSetup.ClusterCreationResponse;
import org.eclipse.jemo.sys.JemoRuntimeSetup.SetupParams;
import org.eclipse.jemo.sys.JemoRuntimeSetup.TerraformJobResponse;
import org.eclipse.jemo.sys.ClusterParams;
import org.eclipse.jemo.sys.ClusterParams.ClusterParam;
import org.eclipse.jemo.sys.internal.TerraformJob;
import org.eclipse.jemo.sys.internal.Util;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.jayway.jsonpath.PathNotFoundException;
import com.microsoft.azure.documentdb.ConnectionPolicy;
import com.microsoft.azure.documentdb.ConsistencyLevel;
import com.microsoft.azure.documentdb.Database;
import com.microsoft.azure.documentdb.Document;
import com.microsoft.azure.documentdb.DocumentClient;
import com.microsoft.azure.documentdb.DocumentClientException;
import com.microsoft.azure.documentdb.DocumentCollection;
import com.microsoft.azure.documentdb.FeedOptions;
import com.microsoft.azure.documentdb.SqlParameter;
import com.microsoft.azure.documentdb.SqlParameterCollection;
import com.microsoft.azure.documentdb.SqlQuerySpec;
import com.microsoft.azure.eventhubs.EventData;
import com.microsoft.azure.eventhubs.EventHubClient;
import com.microsoft.azure.eventhubs.PartitionReceiveHandler;
import com.microsoft.azure.eventhubs.PartitionReceiver;
import com.microsoft.azure.eventhubs.PartitionSender;
import com.microsoft.azure.servicebus.ConnectionStringBuilder;
import com.microsoft.azure.servicebus.ServiceBusException;
import com.microsoft.azure.storage.CloudStorageAccount;
import com.microsoft.azure.storage.StorageCredentialsAccountAndKey;
import com.microsoft.azure.storage.StorageException;
import com.microsoft.azure.storage.blob.*;
import com.microsoft.azure.storage.queue.CloudQueue;
import com.microsoft.azure.storage.queue.CloudQueueClient;
import com.microsoft.azure.storage.queue.CloudQueueMessage;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.ws.Holder;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.util.Config;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.codec.digest.HmacUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.fluent.Form;
import org.apache.http.client.fluent.Request;
import org.apache.http.client.fluent.Response;
import org.apache.http.entity.ContentType;
import org.jetbrains.annotations.NotNull;

/**
 * TODO: we need to bring the test coverage on the Azure Runtime to 100% right now there are no tests and this is bad.
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class MicrosoftAzureRuntime implements CloudRuntime {

    /**
     * Azure specific constants
     */
    private static final String AZURE_RESOURCE_PORTAL = "https://management.azure.com/";

    private static final String TENANT_ID_VAR_NAME = "tenant_id";
    private static final String CLIENT_ID_VAR_NAME = "client_id";
    private static final String CLIENT_SECRET_VAR_NAME = "client_secret";

    private static final String PROP_RESOURCEGROUP = "eclipse.jemo.azure.resourcegroup";
    private static final String PROP_EVENTHUB = "eclipse.jemo.azure.eventhub";
    private static final String PROP_DB = "eclipse.jemo.azure.db";
    private static final String PROP_STORAGE = "eclipse.jemo.azure.storage";
    private static final String PROP_LOG_WORKSPACE = "eclipse.jemo.azure.log-workspace";
    private static final String PROP_KEYVAULT = "eclipse.jemo.azure.keyvault";
    private static final String PROP_MSG_MODEL = "eclipse.jemo.azure.msg.model";

    private static final Set<String> REQUIRED_ACTIONS = new HashSet<>(asList(
            "Microsoft.Resources/subscriptions/read",
            "Microsoft.Storage/storageAccounts/listKeys/action",
            "Microsoft.EventHub/namespaces/eventhubs/write",
            "Microsoft.EventHub/namespaces/AuthorizationRules/listKeys/action",
            "Microsoft.DocumentDB/databaseAccounts/listKeys/action",
            "Microsoft.DocumentDB/databaseAccounts/read",
            "Microsoft.OperationalInsights/workspaces/read",
            "Microsoft.Operationalinsights/workspaces/sharedkeys/read",
            "Microsoft.Authorization/roleAssignments/read",
            "Microsoft.Authorization/roleDefinitions/read",
            "Microsoft.Network/virtualNetworks/read",
            "Microsoft.ManagedIdentity/userAssignedIdentities/read",
            "Microsoft.KeyVault/vaults/read",
            "Microsoft.KeyVault/vaults/secrets/read",
            "Microsoft.KeyVault/vaults/secrets/write"));

    private static String REGION;
    private static AzureCredentials AZURE_CREDENTIALS;

    private static String RESOURCE_GROUP;
    private static String EVENT_HUB_NAMESPACE;
    private static String DATABASE_ACCOUNT;
    private static String STORAGE_ACCOUNT;
    private static String LOG_WORKSPACE;
    private static String KEY_VAULT;
    private static MESSAGE_MODEL MSG_MODEL;

    private static final String STORAGE_MODULE_CONTAINER = "jemopluginlib";
    private String ENCRYPTION_KEY;

    public MicrosoftAzureRuntime() throws IOException {
        if (System.getProperty("eclipse.jemo.azure.encryptionKey") != null) {
            ENCRYPTION_KEY = System.getProperty("eclipse.jemo.azure.encryptionKey");
        } else {
            final Path path = Paths.get("/kv/encryptionKey");
            if (Files.exists(path)) {
                ENCRYPTION_KEY = Files.lines(path).collect(Collectors.joining(""));
            }
        }

        Properties properties = readPropertiesFile();
        RESOURCE_GROUP = readProperty(PROP_RESOURCEGROUP, properties, "jemorg");
        EVENT_HUB_NAMESPACE = readProperty(PROP_EVENTHUB, properties, "jemoehn");
        DATABASE_ACCOUNT = readProperty(PROP_DB, properties, "jemocdba");
        STORAGE_ACCOUNT = readProperty(PROP_STORAGE, properties, "jemosa");
        LOG_WORKSPACE = readProperty(PROP_LOG_WORKSPACE, properties, "jemo-log-workspace");
        KEY_VAULT = readProperty(PROP_KEYVAULT, properties, "jemokv");
        MSG_MODEL = MESSAGE_MODEL.valueOf(readProperty(PROP_MSG_MODEL, properties, QUEUE.name()));
    }

    private static String readProperty(String propertyName, Properties properties, String defaultValue) {
        final String value = readParameterFromJvmOrEnv(propertyName);
        if (value != null) {
            return value;
        }

        return properties == null || properties.getProperty(propertyName) == null ? defaultValue : properties.getProperty(propertyName);
    }

    static class AzureCredentials {
        private final String tenantId, clientId, clientSecret;

        public AzureCredentials(String tenantId, String clientId, String clientSecret) {
            this.tenantId = tenantId;
            this.clientId = clientId;
            this.clientSecret = clientSecret;
        }

        @Override
        public String toString() {
            return "AzureCredentials{" +
                    "tenantId='" + tenantId + '\'' +
                    ", clientId='" + clientId + '\'' +
                    ", clientSecret='" + clientSecret + '\'' +
                    '}';
        }
    }

    private static final AzureCredentials AZURE_CREDENTIALS() {
        if (AZURE_CREDENTIALS == null) {
            if (System.getProperty("eclipse.jemo.azure.tenantid") != null
                    && System.getProperty("eclipse.jemo.azure.clientid") != null
                    && System.getProperty("eclipse.jemo.azure.clientsecret") != null) {
                Jemo.log(Level.FINE, "[AZURE][AZURE_CREDENTIALS] Credentials are found from jvm properties");
                AZURE_CREDENTIALS = new AzureCredentials(
                        System.getProperty("eclipse.jemo.azure.tenantid"),
                        System.getProperty("eclipse.jemo.azure.clientid"),
                        System.getProperty("eclipse.jemo.azure.clientsecret"));
            } else {
                Jemo.log(Level.FINE, "[AZURE][AZURE_CREDENTIALS] Checking if the credentials file exists.");
                AZURE_CREDENTIALS = readCredentialsFromFile();
                if (AZURE_CREDENTIALS == null) {
                    Jemo.log(Level.FINE, "[AZURE][AZURE_CREDENTIALS] Could not find the azure credentials file, reading them from the key vault instead.");

                    AZURE_CREDENTIALS = readFromDiscMountedToKeyVault();
                    if (AZURE_CREDENTIALS != null) {
                        Jemo.log(Level.FINE, "[AZURE][AZURE_CREDENTIALS] Credentials read from key vault: " + AZURE_CREDENTIALS());
                    }
                } else {
                    REGION = readRegionFromFile();
                    Jemo.log(Level.FINE, "Credentials read from the azure credentials file.");
                }
            }
        }

        return AZURE_CREDENTIALS;
    }

    private static AzureCredentials readFromDiscMountedToKeyVault() {
        final Path secretsDirPath = Paths.get("/kv");
        if (!Files.exists(secretsDirPath)) {
            return null;
        }

        try {
            final String clientId = Files.lines(secretsDirPath.resolve("clientId")).collect(Collectors.joining(""));
            final String clientSecret = Files.lines(secretsDirPath.resolve("clientSecret")).collect(Collectors.joining(""));
            final String tenantId = Files.lines(secretsDirPath.resolve("tenantId")).collect(Collectors.joining(""));
            return new AzureCredentials(tenantId, clientId, clientSecret);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static AzureCredentials readCredentialsFromFile() {
        final Path azureDirPath = azureDirectory();
        if (!Files.exists(azureDirPath)) {
            return null;
        }

        final Path credentialsFilePath = azureDirPath.resolve("credentials");
        if (!Files.exists(credentialsFilePath)) {
            return null;
        }

        try (FileInputStream stream = new FileInputStream(credentialsFilePath.toFile())) {
            final Properties properties = new Properties();
            properties.load(stream);

            String tenantId = properties.getProperty("tenant");
            String clientId = properties.getProperty("client");
            String clientSecret = properties.getProperty("key");
            return new AzureCredentials(tenantId, clientId, clientSecret);
        } catch (IOException e) {
            Jemo.log(Level.FINE, "[AZURE][readCredentialsFromFile] I was unable to read the credentialsFilePath from %s because of the error %s", azureDirPath, e.getMessage());
            return null;
        }
    }

    private static String readRegionFromFile() {
        final Path credentialsFilePath = azureDirectory();
        if (!Files.exists(credentialsFilePath)) {
            return null;
        }

        try (FileInputStream stream = new FileInputStream(credentialsFilePath.resolve("region").toFile())) {
            final Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty("region");
        } catch (IOException e) {
            Jemo.log(Level.FINE, "[AZURE][readCredentialsFromFile] I was unable to read the credentials from %s because of the error %s", credentialsFilePath, e.getMessage());
            return null;
        }
    }

    @Override
    public void start(AbstractJemo jemoServer) {
    }

    enum HttpMode {
        GET, POST, PUT, PATCH, DELETE
    }

    private HttpResponse sendRequestWithSubscriptionAndResourceGroup(HttpMode httpMode, String uriSuffix, String requestBody) {
        return Util.F(null, x -> {
            final String subId = getSubscriptionId(getAuthToken());
            final String uri = AZURE_RESOURCE_PORTAL + "subscriptions/" + subId + "/resourceGroups/" + RESOURCE_GROUP + uriSuffix;
            return sendRequest(httpMode, uri, requestBody);
        });
    }

    private HttpResponse sendRequestWithSubscription(HttpMode httpMode, String uriSuffix, String requestBody) {
        return Util.F(null, x -> {
            final String subId = getSubscriptionId(getAuthToken());
            final String uri = AZURE_RESOURCE_PORTAL + "subscriptions/" + subId + uriSuffix;
            return sendRequest(httpMode, uri, requestBody);
        });
    }

    @NotNull
    private HttpResponse sendRequest(HttpMode httpMode, String uri, String requestBody) {
        return Util.F(null, x -> {
            final String authToken = getAuthToken();
            final Request request = createRequest(httpMode, uri)
                    .addHeader("Authorization", "Bearer " + authToken);
            if (httpMode != GET) {
                request.bodyString(requestBody, ContentType.APPLICATION_JSON);
            }
            final HttpResponse response = request.execute().returnResponse();
            if (response.getStatusLine().getStatusCode() >= 300) {
                throw new IllegalStateException(response.getStatusLine().getStatusCode() +
                        " - " + response.getStatusLine().getReasonPhrase() + " - " + responseBody(response));
            }
            return response;
        });
    }

    @NotNull
    private Request createRequest(HttpMode httpMode, String uri) {
        switch (httpMode) {
            case GET:
                return Request.Get(uri);
            case POST:
                return Request.Post(uri);
            case PUT:
                return Request.Put(uri);
            case PATCH:
                return Request.Patch(uri);
            case DELETE:
                return Request.Delete(uri);
            default:
                throw new IllegalStateException("httpMode not supported yet: " + httpMode);
        }
    }

    private static String responseBody(HttpResponse response) {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        return Util.F(byteOut, bo -> {
            response.getEntity().writeTo(byteOut);
            return byteOut.toString("UTF-8");
        });
    }

    @Override
    public void delete(String category, String key) {
        executeFunction(rt -> {
            getStorageContainer(category).getBlockBlobReference(key).deleteIfExists();
            return null;
        }, this);
    }

    @Override
    public void write(String category, String path, String key, InputStream dataStream) {
        executeFunction(rt -> {
            File tmpFile = JemoPluginManager.cacheStreamToFile(dataStream);
            try {
                getStorageContainer(category).getBlockBlobReference(path + "/" + key).upload(new FileInputStream(tmpFile), tmpFile.length());
                Jemo.log(Level.FINE, "[AZURE][write][%s][%s][%s] stored", category, path, key);
                return null;
            } finally {
                tmpFile.delete();
            }
        }, this);
    }

    @Override
    public InputStream read(String category, String path, String key) {
        return executeFunction(rt -> {
            try {
                com.microsoft.azure.storage.blob.CloudBlob blob = getStorageContainer(category).getBlobReferenceFromServer(path + "/" + key);
                if (blob != null) {
                    return blob.openInputStream();
                }
            } catch (StorageException e) {
                Jemo.log(Level.FINE, "[AZURE][read][%s][%s][%s] not found", category, path, key);
                return null;
            }

            return null;
        }, this);
    }

    @Override
    public void uploadModule(String pluginFile, InputStream in, long moduleSize) {
        executeFunction(rt -> {
            getStorageContainer(STORAGE_MODULE_CONTAINER).getBlockBlobReference(pluginFile).upload(in, moduleSize);
            return null;
        }, this);
    }

    @Override
    public void dropNoSQLTable(String tableName) {
        executeFunction(rt -> {
            Iterator<DocumentCollection> collectionList = getDocumentDB().readCollections(noSQLDB.getSelfLink(), null).getQueryIterator();
            DocumentCollection collection = null;
            while (collectionList.hasNext()) {
                collection = collectionList.next();
                if (collection.getId().equalsIgnoreCase(tableName)) {
                    break;
                }
            }
            if (collection != null) {
                getDocumentDB().deleteCollection(collection.getSelfLink(), null);
            }
            return null;
        }, this);
    }

    @Override
    public void deleteNoSQL(String tableName, SystemDBObject... data) {
        if (data != null && data.length > 0) {
            executeFunction((rt) -> {
                DocumentClient docDb = getDocumentDB();
                DocumentCollection docTbl = docDb.readCollections(noSQLDB.getSelfLink(), null).getQueryIterable().toList().stream().filter(tbl -> tbl.getId().equalsIgnoreCase(tableName)).findAny().orElse(null);
                AtomicInteger ctr = new AtomicInteger(1);
                SqlParameterCollection paramList = new SqlParameterCollection();
                String sqlQuery = "select * from root r where " + Arrays.asList(data).stream().map(obj -> {
                    int paramId = ctr.incrementAndGet();
                    paramList.add(new SqlParameter("@p" + paramId, obj.getId()));
                    return "r.id = @p" + paramId;
                }).collect(Collectors.joining(" OR "));
                FeedOptions feedOpts = new FeedOptions();
                feedOpts.setEnableCrossPartitionQuery(true);
                feedOpts.setPageSize(data.length);
                docDb.queryDocuments(docTbl.getSelfLink(), new SqlQuerySpec(sqlQuery, paramList), feedOpts).getQueryIterable().toList().stream().forEach(azureDoc -> {
                    try {
                        docDb.deleteDocument(azureDoc.getSelfLink(), null);
                    } catch (DocumentClientException ex) {
                        throw new RuntimeException(ex);
                    }
                });

                return null;
            }, this);
        }
    }

    enum MESSAGE_MODEL {
        EVENTHUB, QUEUE
    }

    private String subscriptionId = null;
    private DocumentClient documentDb = null;
    private Database noSQLDB = null;
    private CloudBlobClient blobStorage = null;
    private CloudStorageAccount cloudStorage = null;
    private CloudQueueClient queueStorage = null;
    private CloudBlobContainer queueStorageContainer = null;
    private Map<String, CloudBlobContainer> blobContainerMap = new HashMap<>();
    private String eventHubNamespaceKey = null;
    private String lastAuthToken = null;
    private long authTokenExpiresOn = 0;
    private Map<String, CountDownLatch> queuePollMap = new HashMap<>();
    private Map<String, PartitionReceiver> queueRecieverMap = new HashMap<>();
    private Map<String, EventHubClient> eventHubMap = new HashMap<>();
    private Map<Integer, LogMetadata> moduleLogMap = new HashMap<>();
    private ExecutorService EventProcessingThreadPool = Executors.newCachedThreadPool();

    private String formatQueue(String queueName) {
        return queueName.toLowerCase();
    }

    private byte[] serializeObject(Object obj) throws IOException {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (ObjectOutputStream objOut = new ObjectOutputStream(byteOut)) {
            objOut.writeObject(obj);
        }

        return byteOut.toByteArray();
    }

    private Object deserializeObject(InputStream data, ClassLoader clsLoader) throws IOException, ClassNotFoundException {
        try (ObjectInputStream objIn = new ObjectInputStream(data) {
            @Override
            protected Class<?> resolveClass(ObjectStreamClass desc) throws IOException, ClassNotFoundException {
                return Class.forName(desc.getName(), false, clsLoader);
            }
        }) {
            return objIn.readObject();
        }
    }

    @Override
    public void store(String key, Object data) {
        store(STORAGE_MODULE_CONTAINER, key, data);
    }

    @Override
    public <T> T retrieve(String key, Class<T> objType) {
        return retrieve(STORAGE_MODULE_CONTAINER, key, objType);
    }

    @Override
    public void store(String category, String key, Object data) {
        executeFunction(rt -> {
            byte[] pluginBytes = serializeObject(data);
            getStorageContainer(category).getBlockBlobReference(key.endsWith(".moduledata") ? key : key + ".moduledata").uploadFromByteArray(pluginBytes, 0, pluginBytes.length);
            Jemo.log(Level.FINE, "[AZURE][store][%s][%s] stored with value %s", category, key, data.toString());
            return null;
        }, this);
    }

    @Override
    public <T> T retrieve(String category, String key, Class<T> objType) {
        return executeFunction(rt -> {
            try {
                String storageKey = key + ".moduledata";
                CloudBlobContainer moduleContainer = getStorageContainer(category);
                //we need a test to prove that when we retrieve and write to a blob at the same time with multiple threads this does not fail.
                com.microsoft.azure.storage.blob.CloudBlob blob = moduleContainer.getBlobReferenceFromServer(storageKey);
                if (blob != null) {
                    CloudBlob cloudBlob = new CloudBlob(storageKey, blob.getProperties().getLastModified().getTime(), blob.getProperties().getLength(), blob.openInputStream());
                    Object obj = deserializeObject(cloudBlob.getDataStream(), objType.getClassLoader());
                    Jemo.log(Level.FINE, "[AZURE][retrieve][%s][%s] retrieved with value %s", category, key, obj.toString());
                    if (objType.isAssignableFrom(obj.getClass())) {
                        return objType.cast(obj);
                    }
                }
            } catch (StorageException storageEx) {
            } //if there is a storage exception then we should return null
            return null;
        }, this);
    }

    private static class AzureDocumentWrapper {
        private String id = null;
        private SystemDBObject data = null;

        public AzureDocumentWrapper() {
        }

        public AzureDocumentWrapper(SystemDBObject data) {
            this.id = data.getId();
            this.data = data;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public SystemDBObject getData() {
            return data;
        }

        public void setData(SystemDBObject data) {
            this.data = data;
        }
    }

    private String getAuthToken() throws IOException {
        if (lastAuthToken == null || authTokenExpiresOn <= System.currentTimeMillis()) {
            getAuthToken(AZURE_CREDENTIALS());
        }

        return lastAuthToken;
    }

    private void getAuthToken(AzureCredentials azureCredentials) throws IOException {
        HttpResponse response = Request.Post("https://login.microsoftonline.com/" + azureCredentials.tenantId + "/oauth2/token")
                .addHeader("Content-Type", "application/x-www-form-urlencoded")
                .bodyForm(Form.form().add("grant_type", "client_credentials")
                        .add("client_id", azureCredentials.clientId)
                        .add("client_secret", azureCredentials.clientSecret)
                        .add("resource", AZURE_RESOURCE_PORTAL).build()).execute().returnResponse();

        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        response.getEntity().writeTo(byteOut);
        String authJsonResponse = new String(byteOut.toByteArray(), "UTF-8");
        if (response.getStatusLine().getStatusCode() != 200) {
            throw new IllegalStateException("Authentication failed. Http response body is: \n" + authJsonResponse);
        }
        Map<String, String> authResult = fromJSONString(Map.class, authJsonResponse);
        final String accessToken = authResult.get("access_token");
        lastAuthToken = accessToken;
        authTokenExpiresOn = (Long.parseLong(authResult.get("expires_on")) - 5) * 1000; //we will actually consider the token invalid 5 seconds before it actually expires.
    }

    private String getSubscriptionId(String accessToken) throws IOException {
        if (subscriptionId == null) {
            Map<String, List<Map<String, String>>> subscriptionList = fromJSONString(Map.class, Request.Get(AZURE_RESOURCE_PORTAL + "subscriptions?api-version=2016-06-01")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("x-ms-version", "2016-06-01").execute().returnContent().asString(Charset.forName("UTF-8")));
            subscriptionId = subscriptionList.get("value").get(0).get("subscriptionId");
        }

        return subscriptionId;
    }

    @Override
    public String defineQueue(String queueName) {
        return executeFunction((rt) -> {
            //on azure this is slightly different because we will have to create an event hub for each queue so the steps are.
            //1. lets assume that the namespace already exists.
            String authToken = getAuthToken();
            String subId = getSubscriptionId(authToken);

            switch (MSG_MODEL) {
                case EVENTHUB:
                    HttpResponse response = Request.Put(AZURE_RESOURCE_PORTAL + "subscriptions/" + subId + "/resourceGroups/" + RESOURCE_GROUP + "/providers/Microsoft.EventHub/namespaces/" + EVENT_HUB_NAMESPACE + "/eventhubs/" + queueName + "?api-version=2015-08-01")
                            .addHeader("Authorization", "Bearer " + authToken)
                            .addHeader("x-ms-version", "2016-06-01")
                            .bodyString("{ \"properties\": { \"messageRetentionInDays\": 1, \"partitionCount\": 32 } }", ContentType.APPLICATION_JSON).execute().returnResponse();

                    if (response.getStatusLine().getStatusCode() == 200) {
                        Map<String, Object> retval = fromJSONString(Map.class, responseBody(response));
                        return retval.get("id").toString();
                    } else {
                        throw new Exception("[" + queueName + "] queue could not be defined [" + response.getStatusLine().getStatusCode() + "] azure return value: " + responseBody(response));
                    }
                case QUEUE:
                    CloudQueueClient cloudQueue = getQueueStorage();
                    CloudQueue queue = cloudQueue.getQueueReference(formatQueue(queueName));
                    try {
                        queue.createIfNotExists();
                    } catch (StorageException storageEx) {
                        if (storageEx.getMessage().equals("The specified queue is being deleted.")) {
                            //we should wait until this operation is complete and re-create the queue
                            Jemo.log(Level.INFO, "[AZURE][%s] the queue is being deleted but is actually needed we are waiting 20 seconds for the race condition to be resolved and then we will retry the operation.", queueName);
                            Thread.sleep(20000); //wait for 20 seconds before attempting recovery.
                            return defineQueue(queueName);
                        }
                    }
                    return queue.getName();
            }
            return null;
        }, this);
    }

    @Override
    public void storeModuleList(String moduleJar, List<String> moduleList) throws Throwable {
        executeFunction(rt -> {
            uploadModule(moduleJar + ".modulelist", Jemo.toJSONString(moduleList).getBytes("UTF-8"));
            return null;
        }, this);
    }

    @Override
    public List<String> getModuleList(String moduleJar) throws Throwable {
        return executeFunction(rt -> {
            CloudBlob ref = getModule(moduleJar + ".modulelist");
            if (ref != null) {
                return Jemo.fromJSONArray(String.class, new String(ref.getData(), "UTF-8"));
            }

            return null;
        }, this);
    }

    @Override
    public CloudBlob getModule(String moduleJar) throws IOException {
        return executeFunction(rt -> {
            try {
                CloudBlobContainer moduleContainer = getModuleContainer();
                com.microsoft.azure.storage.blob.CloudBlob blob = moduleContainer.getBlobReferenceFromServer(moduleJar);
                return new CloudBlob(moduleJar, blob.getProperties().getLastModified().getTime(), blob.getProperties().getLength(), blob.openInputStream());
            } catch (StorageException storageEx) {
                return null;
            }
        }, this);
    }

    @Override
    public Long getModuleInstallDate(String moduleJar) throws IOException {
        return executeFunction(rt -> {
            CloudBlob blob = getModule(moduleJar + ".installed");
            if (blob != null) {
                return Long.parseLong(new String(blob.getData(), "UTF-8"));
            }

            return null;
        }, this);
    }

    @Override
    public void setModuleInstallDate(String moduleJar, long installDate) throws IOException {
        executeFunction(rt -> {
            uploadModule(moduleJar + ".installed", String.valueOf(installDate).getBytes("UTF-8"));
            return null;
        }, this);
    }

    private static class LogMetadata {
        private String customerId = null;
        private String provisioningState = null;
        private String logAuthKey = null;
        private String logName;

        public LogMetadata(String logName) {
            this.logName = logName;
        }

        public void init(MicrosoftAzureRuntime runtime) throws IOException {
            String accessToken = runtime.getAuthToken();
            String subId = runtime.getSubscriptionId(accessToken);

            final HttpResponse response = Request.Get(AZURE_RESOURCE_PORTAL + "/subscriptions/" + subId +
                    "/resourcegroups/" + RESOURCE_GROUP + "/providers/Microsoft.OperationalInsights/workspaces/" + URLEncoder.encode(logName, "UTF-8") + "?api-version=2015-11-01-preview")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("x-ms-version", "2015-11-01-preview").execute().returnResponse();

            Map<String, Map<String, String>> logObj = Jemo.fromJSONString(Map.class, responseBody(response));
            this.customerId = logObj.get("properties").get("customerId");
            this.provisioningState = logObj.get("properties").get("provisioningState");

            final HttpResponse logAuthKeyResponse = Request.Post(AZURE_RESOURCE_PORTAL + "/subscriptions/" + subId + "/resourcegroups/" + RESOURCE_GROUP + "/providers/Microsoft.OperationalInsights/workspaces/" + URLEncoder.encode(logName, "UTF-8") + "/sharedKeys?api-version=2015-11-01-preview")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("x-ms-version", "2015-11-01-preview").execute().returnResponse();
            final String logAuthKeyResponseBody = responseBody(logAuthKeyResponse);

            if (logAuthKeyResponse.getStatusLine().getStatusCode() >= 300) {
                throw new IllegalStateException("Failed to get log auth key, http status code: [" + logAuthKeyResponse.getStatusLine().getStatusCode() + "] azure return value: " + logAuthKeyResponseBody);
            }

            Map<String, String> logAuthKeys = Jemo.fromJSONString(Map.class, logAuthKeyResponseBody);
            this.logAuthKey = logAuthKeys.get("primarySharedKey");
        }

        public String getCustomerId() {
            return customerId;
        }

        public void setCustomerId(String customerId) {
            this.customerId = customerId;
        }

        public String getProvisioningState() {
            return provisioningState;
        }

        public void setProvisioningState(String provisioningState) {
            this.provisioningState = provisioningState;
        }

        public String getLogAuthKey() {
            return logAuthKey;
        }

        public void setLogAuthKey(String logAuthKey) {
            this.logAuthKey = logAuthKey;
        }

        public String getLogName() {
            return logName;
        }

        public void setLogName(String logName) {
            this.logName = logName;
        }

        public void write(List<CloudLogEvent> logEvents) throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException, JsonProcessingException, ClientProtocolException, IOException {
            if ("Succeeded".equals(getProvisioningState())) {
                SimpleDateFormat msXDateFormat = new SimpleDateFormat("EEE, dd MMM YYYY HH:mm:ss zzz");
                msXDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
                String xMsDate = msXDateFormat.format(new java.util.Date());
                String bodyString = Jemo.toJSONString(logEvents);
                Mac sha256_HMAC = Mac.getInstance("HmacSHA256");
                SecretKeySpec secret_key = new SecretKeySpec(Base64.decodeBase64(logAuthKey), "HmacSHA256");
                sha256_HMAC.init(secret_key);
                String message = "POST\n" + bodyString.length() + "\napplication/json\nx-ms-date:" + xMsDate + "\n/api/logs";
                //String signature = Base64.encodeBase64String(sha256_HMAC.doFinal(message.getBytes("US-ASCII")));
                String signature = Base64.encodeBase64String(HmacUtils.hmacSha256(Base64.decodeBase64(logAuthKey), message.getBytes("US-ASCII")));
                Request.Post("https://" + customerId + ".ods.opinsights.azure.com/api/logs?api-version=2016-04-01")
                        .setHeader("Content-Type", "application/json")
                        .setHeader("Authorization", "SharedKey " + customerId + ":" + signature)
                        .setHeader("Log-Type", "APPLOG")
                        .setHeader("x-ms-date", xMsDate)
                        .bodyByteArray(bodyString.getBytes("UTF-8")).execute().returnResponse().getEntity().writeTo(System.out);
            }
        }
    }

    @Override
    public void log(List<CloudLogEvent> eventList) {
        //we need to make sure this is logged to the azure event log
        if (eventList != null && !eventList.isEmpty()) {
            //we need to organise the logs which need to be added by module id
            Map<Integer, List<CloudLogEvent>> eventMap = eventList.parallelStream().collect(Collectors.groupingBy(CloudLogEvent::getModuleId));
            eventMap.entrySet().parallelStream().forEach(e -> {
                executeFunction(x -> {
                    LogMetadata logEngine = moduleLogMap.get(e.getKey());
                    if (logEngine == null) {
                        logEngine = new LogMetadata(LOG_WORKSPACE);
                        moduleLogMap.put(e.getKey(), logEngine);
                        logEngine.init(this);
                    }
                    try {
                        logEngine.write(e.getValue());
                    } catch (IOException | InvalidKeyException ex) {
                        Jemo.log(Level.WARNING, "[AZURE][%d] events were discared because of an error communicating with OMS %s", eventList.size(), ex.getMessage());
                    }
                    return null;
                }, this);
            });

        }
    }

    @Override
    public void deleteQueue(String queueId) {
        executeFunction(rt -> {
            //DELETE /subscriptions/{subscriptionId}/resourceGroups/{resourceGroupName}/providers/Microsoft.EventHub/namespaces/{namespaceName}/eventhubs/{eventHubName}?api-version=2015-08-01
            String authKey = getAuthToken();
            switch (MSG_MODEL) {
                case EVENTHUB:
                    String response = Request.Delete(AZURE_RESOURCE_PORTAL + queueId + "?api-version=2015-08-01")
                            .addHeader("Authorization", "Bearer " + authKey)
                            .addHeader("x-ms-version", "2015-08-01").execute().returnContent().asString(Charset.forName("UTF-8"));
                    break;
                case QUEUE:
                    getQueueStorage().getQueueReference(formatQueue(queueId)).deleteIfExists();
                    break;
            }

            return null;
        }, this);
    }

    private CloudBlobContainer getMessageContainer() throws ClientProtocolException, IOException, URISyntaxException, StorageException {
        if (queueStorageContainer == null) {
            queueStorageContainer = getBlobStorage().getContainerReference("queue-msg-data");
            queueStorageContainer.createIfNotExists();
        }

        return queueStorageContainer;
    }

    @Override
    public String sendMessage(String queueId, String jsonMessage) {
        return executeFunction(rt -> {
            switch (MSG_MODEL) {
                case EVENTHUB:
                    EventHubClient eHub = getEventHub(queueId);
                    PartitionSender sender = eHub.createPartitionSenderSync("0");
                    sender.sendSync(new EventData(jsonMessage.getBytes("UTF-8")));
                    return UUID.randomUUID().toString();
                case QUEUE:
                    try {
                        CloudBlobContainer msgStorage = getMessageContainer();
                        byte[] msgBytes = jsonMessage.getBytes("UTF-8");
                        String msgBlobId = UUID.randomUUID().toString();
                        msgStorage.getBlockBlobReference(msgBlobId).uploadFromByteArray(msgBytes, 0, msgBytes.length);
                        CloudQueueMessage msg = new CloudQueueMessage(msgBlobId);
                        CloudQueue queueRef = getQueueStorage().getQueueReference(formatQueue(queueId));
                        if (queueRef.exists()) {
                            queueRef.addMessage(msg);
                        } else {
                            Jemo.log(Level.WARNING, "[AZURE][%s] does not exist. The message [%s] will not be sent", queueId, jsonMessage);
                        }
                        return msg.getId();
                    } catch (StorageException storageEx) {
                        throw new RuntimeException("The queue [" + queueId + "] does not exist", storageEx);
                    }
            }

            return null;
        }, this);
    }

    @Override
    public String getQueueId(String queueName) {
        return executeFunction(rt -> {
            switch (MSG_MODEL) {
                case EVENTHUB:
                    return "/subscriptions/" + getSubscriptionId(getAuthToken()) + "/resourceGroups/" + RESOURCE_GROUP + "/providers/Microsoft.EventHub/namespaces/" + EVENT_HUB_NAMESPACE + "/eventhubs/" + queueName;
                case QUEUE:
                    return formatQueue(queueName);
            }
            return null;
        }, this);
    }

    @Override
    public List<String> listQueueIds(String location) {
        return listQueueIds(location, false);
    }

    public EventHubClient getEventHub(String queueId) throws ClientProtocolException, IOException, ServiceBusException {
        //POST /subscriptions/{subscriptionId}/resourceGroups/{resourceGroupName}/providers/Microsoft.EventHub/namespaces/{namespaceName}/eventhubs/{eventHubName}/authorizationRules/{authorizationRuleName}/ListKeys?api-version=2015-08-01
        if (eventHubNamespaceKey == null) {
            String accessToken = getAuthToken();
            String subId = getSubscriptionId(accessToken);
            final HttpResponse response = Request.Post(AZURE_RESOURCE_PORTAL + "/subscriptions/" + subId + "/resourceGroups/" + RESOURCE_GROUP + "/providers/Microsoft.EventHub/namespaces/" + EVENT_HUB_NAMESPACE + "/AuthorizationRules/RootManageSharedAccessKey/listKeys?api-version=2015-08-01")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("x-ms-version", "2015-08-01").execute().returnResponse();

            if (response.getStatusLine().getStatusCode() >= 300) {
                throw new IllegalStateException("Failed to list keys under eventhub namespace, http status code: [" + response.getStatusLine().getStatusCode() + "] azure return value: " + responseBody(response));
            }

            eventHubNamespaceKey = getValueFromJSON(responseBody(response), "$.primaryKey");
        }

        EventHubClient client = eventHubMap.get(queueId);
        if (client == null) {
            String queueName = queueId.substring(queueId.lastIndexOf('/') + 1);
            ConnectionStringBuilder connString = new ConnectionStringBuilder(EVENT_HUB_NAMESPACE, queueName, "RootManageSharedAccessKey", eventHubNamespaceKey);
            client = EventHubClient.createFromConnectionStringSync(connString.toString());
            eventHubMap.put(queueId, client);
        }

        return client;
    }

    @Override
    public int pollQueue(String queueId, CloudQueueProcessor processor) throws QueueDoesNotExistException {
        switch (MSG_MODEL) {
            case EVENTHUB:
                return pollEventHub(queueId, processor);
            case QUEUE:
                return pollStorageQueue(queueId, processor);
        }

        return 0;
    }

    public int pollStorageQueue(String queueId, CloudQueueProcessor processor) throws QueueDoesNotExistException {
        try {
            //we should store a file on blob storage which contains the last time this queue was polled but only if this is an instance
            //queue.
            if (queueId.toLowerCase().startsWith("jemo-") && !queueId.toLowerCase().endsWith("-work-queue")) {
                store("jemo-queue-usage", queueId, System.currentTimeMillis());
            }
            CloudQueue queue = getQueueStorage().getQueueReference(queueId);
            queue.createIfNotExists();
            List<CloudQueueMessage> msgList = new ArrayList<>();
            List<Callable<Object>> msgRunList = new ArrayList<>();
            long startRetrieval = System.currentTimeMillis();
            try {
                StreamSupport.stream(queue.retrieveMessages(32).spliterator(), true).forEach(cmsg -> {
                    msgList.add(cmsg);
                    try {
                        queue.deleteMessage(cmsg);
                        try {
                            CloudBlockBlob msgBlob = getMessageContainer().getBlockBlobReference(cmsg.getMessageContentAsString());
                            JemoMessage msg = Jemo.fromJSONString(JemoMessage.class, msgBlob.downloadText());
                            msgBlob.delete();
                            if (msg.getSourceInstance() != null) {
                                processor.processMessage(msg);
                            }
                        } catch (IOException | StorageException | URISyntaxException ioex) {
                        }
                    } catch (StorageException ex) {
                    }
                });
            } catch (StorageException ex) {
            }
			/*if(!msgList.isEmpty()) {
				queue.downloadAttributes();
				Jemo.log(Level.FINE, "[AZURE][%s] queue processing complete processed [%d] remaining [%d] - retrieval [%d ms] - processing [%d ms]",
					queueId, msgList.size(), queue.getApproximateMessageCount(), endRetrieval - startRetrieval, endProcessing - endRetrieval);
			}*/
            return msgList.size();
        } catch (StorageException | URISyntaxException | IOException storeEx) {
            throw new QueueDoesNotExistException(String.format("[AZURE][%s] polling failed because of the error: %s", queueId, storeEx.getMessage()));
        }
    }

    public int pollEventHub(String queueId, CloudQueueProcessor processor) throws QueueDoesNotExistException {
        return executeFunction(rt -> {
            Holder<Throwable> lastError = new Holder<>();
            Holder<Integer> numMessages = new Holder<>(0);
            try {
                EventHubClient hubClient = getEventHub(queueId);
                PartitionReceiver rcv = hubClient.createReceiverSync(EventHubClient.DEFAULT_CONSUMER_GROUP_NAME, "0", Instant.now());
                CountDownLatch latch = queuePollMap.get(queueId);
                if (latch != null) {
                    PartitionReceiver prevReciever = queueRecieverMap.get(queueId);
                    prevReciever.closeSync();
                    queueRecieverMap.remove(queueId);
                    queuePollMap.remove(queueId);
                }
                final CountDownLatch pollLatch = new CountDownLatch(1);
                queueRecieverMap.put(queueId, rcv);
                queuePollMap.put(queueId, latch);
                rcv.setReceiveHandler(new PartitionReceiveHandler(10) {
                    @Override
                    public void onReceive(Iterable<EventData> itr) {
                        ArrayList<EventData> eventList = new ArrayList<>();
                        itr.forEach(e -> eventList.add(e));
                        numMessages.value += eventList.size();
                        eventList.parallelStream().forEach(e -> {
                            try {
                                JemoMessage msg = Jemo.fromJSONString(JemoMessage.class, new String(e.getBody(), "UTF-8"));
                                if (msg.getSourceInstance() != null) {
                                    processor.processMessage(msg);
                                }
                            } catch (IOException encEx) {
                            }
                        });
                    }

                    @Override
                    public void onError(Throwable ex) {
                        lastError.value = ex;
                        try {
                            rcv.closeSync();
                        } catch (ServiceBusException svcBusEx) {
                        }
                        pollLatch.countDown();
                    }
                });
                pollLatch.await(); //wait until there is an error.
                queueRecieverMap.remove(queueId);
                queuePollMap.remove(queueId);
            } catch (Throwable ex) {
                lastError.value = ex;
            }
            //we need to wait for the process to terminate
            if (lastError.value != null) {
                Jemo.log(Level.WARNING, "[%s][%s] Error thrown while polling queue %s", CloudProvider.AZURE.name(), queueId, JemoError.toString(lastError.value));
            }
            return numMessages.value;
        }, this);
    }

    private DocumentClient getDocumentDB() throws ClientProtocolException, IOException, DocumentClientException {
        if (documentDb == null) {
            String accessToken = getAuthToken();
            String subId = getSubscriptionId(accessToken);
            HttpResponse response = Request.Post(AZURE_RESOURCE_PORTAL + "/subscriptions/" + subId + "/resourceGroups/" + RESOURCE_GROUP + "/providers/Microsoft.DocumentDB/databaseAccounts/" + DATABASE_ACCOUNT + "/listKeys?api-version=2015-04-08")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("x-ms-version", "2015-04-08").execute().returnResponse();
            if (response.getStatusLine().getStatusCode() >= 300) {
                throw new IllegalStateException("Failed to list keys under database account, http status code: [" + response.getStatusLine().getStatusCode() + "] azure return value: " + responseBody(response));
            }

            String primaryKey = getValueFromJSON(responseBody(response), "$.primaryMasterKey");

            //we should also get the URI for the document db account.
            response = Request.Get(AZURE_RESOURCE_PORTAL + "/subscriptions/" + subId + "/resourceGroups/" + RESOURCE_GROUP + "/providers/Microsoft.DocumentDB/databaseAccounts/" + DATABASE_ACCOUNT + "?api-version=2015-04-08")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("x-ms-version", "2015-04-08").execute().returnResponse();

            if (response.getStatusLine().getStatusCode() >= 300) {
                throw new IllegalStateException("Failed to get document db, http status code: [" + response.getStatusLine().getStatusCode() + "] azure return value: " + responseBody(response));
            }

            String endpoint = getValueFromJSON(responseBody(response), "$.properties.documentEndpoint");
            documentDb = new DocumentClient(endpoint, primaryKey, ConnectionPolicy.GetDefault(), ConsistencyLevel.Session);

            //we need to make sure a database named DATABASE_ACCOUNT exists
            Holder<Database> db = new Holder<>();
            documentDb.readDatabases(null).getQueryIterable().forEach(dbr -> {
                if (dbr.getId().equals(DATABASE_ACCOUNT)) {
                    db.value = dbr;
                }
            });
            if (db.value == null) {
                //we should create the database.
                db.value = new Database();
                db.value.setId(DATABASE_ACCOUNT);
                db.value = documentDb.createDatabase(db.value, null).getResource();
            }

            this.noSQLDB = db.value;
        }
        return documentDb;
    }

    @Override
    public boolean hasNoSQLTable(String tableName) {
        return executeFunction((rt) -> {
            DocumentClient docDb = getDocumentDB();
            return docDb.readCollections(noSQLDB.getSelfLink(), null).getQueryIterable().toList().parallelStream().anyMatch(tbl -> tbl.getId().equalsIgnoreCase(tableName));
        }, this);
    }

    @Override
    public void createNoSQLTable(String tableName) {
        if (!hasNoSQLTable(tableName)) {
            executeFunction((rt) -> {
                DocumentClient docDb = getDocumentDB();
                DocumentCollection docTable = new DocumentCollection();
                docTable.setId(tableName);
                docDb.createCollection(noSQLDB.getSelfLink(), docTable, null);
                return null;
            }, this);
        }
    }

    @Override
    public <T> List<T> listNoSQL(String tableName, Class<T> objectType) {
        return executeFunction((rt) -> {
            List retval = new ArrayList<>();
            DocumentClient docDb = getDocumentDB();
            DocumentCollection docTbl = docDb.readCollections(noSQLDB.getSelfLink(), null).getQueryIterable().toList().stream().filter(tbl -> tbl.getId().equalsIgnoreCase(tableName)).findAny().orElse(null);
            List<Document> docList = docDb.queryDocuments(docTbl.getSelfLink(), new SqlQuerySpec("SELECT * FROM root r"), null).getQueryIterable().toList();
            docList.forEach(doc -> retval.add(doc.getObject("data", objectType)));
            return retval;
        }, this);
    }

    @Override
    public <T> List<T> queryNoSQL(String tableName, Class<T> objectType, String... pkList) {
        return executeFunction((rt) -> {
            if (pkList == null || pkList.length == 0) {
                return listNoSQL(tableName, objectType);
            }
            List retval = new ArrayList<>();
            DocumentClient docDb = getDocumentDB();
            String orSql = IntStream.range(0, pkList.length).mapToObj(i -> i).collect(Collectors.mapping(pk -> "r.id = @p" + String.valueOf(pk), Collectors.joining(" OR ")));
            List<SqlParameter> paramList = IntStream.range(0, pkList.length).mapToObj(i -> i).collect(Collectors.mapping(pk -> new SqlParameter("@p" + String.valueOf(pk), pkList[pk]), toList()));
            DocumentCollection docTbl = docDb.readCollections(noSQLDB.getSelfLink(), null).getQueryIterable().toList().stream().filter(tbl -> tbl.getId().equalsIgnoreCase(tableName)).findAny().orElse(null);
            List<Document> docList = docDb.queryDocuments(docTbl.getSelfLink(), new SqlQuerySpec("SELECT * FROM root r WHERE "
                    + orSql, new SqlParameterCollection(paramList)), null).getQueryIterable().toList();
            docList.forEach(doc -> retval.add(doc.getObject("data", objectType)));
            return retval;
        }, this);
    }

    @Override
    public <T> T getNoSQL(String tableName, String id, Class<T> objectType) throws IOException {
        return executeFunction((rt) -> {
            DocumentClient docDb = getDocumentDB();
            DocumentCollection docTbl = docDb.readCollections(noSQLDB.getSelfLink(), null).getQueryIterable().toList().stream().filter(tbl -> tbl.getId().equalsIgnoreCase(tableName)).findAny().orElse(null);
            List<Document> docList = docDb.queryDocuments(docTbl.getSelfLink(), new SqlQuerySpec("SELECT * FROM root r WHERE r.id=@id", new SqlParameterCollection(new SqlParameter("@id", id))), null).getQueryIterable().toList();
            if (!docList.isEmpty()) {
                return docList.get(0).getObject("data", objectType);
            }
            return null;
        }, this);
    }

    @Override
    public void saveNoSQL(String tableName, SystemDBObject... data) {
        if (data != null && data.length > 0) {
            executeFunction((rt) -> {
                DocumentClient docDb = getDocumentDB();
                DocumentCollection docTbl = docDb.readCollections(noSQLDB.getSelfLink(), null).getQueryIterable().toList().stream().filter(tbl -> tbl.getId().equalsIgnoreCase(tableName)).findAny().orElse(null);
                Arrays.asList(data).parallelStream().forEach(obj -> {
                    String jsonObj = null;
                    try {
                        AzureDocumentWrapper azureDoc = new AzureDocumentWrapper(obj);
                        jsonObj = Jemo.toJSONString(azureDoc);
                        docDb.upsertDocument(docTbl.getSelfLink(), new Document(jsonObj), null, true);
                    } catch (DocumentClientException | JsonProcessingException docEx) {
                        throw new RuntimeException(jsonObj, docEx);
                    }
                });
                return null;
            }, this);
        }
    }

    @Override
    public void watchdog(String location, String instanceId, String instanceQueueUrl) {
        //it would be good in the watchdog process to sweep the queue storage to get rid of dead ugly queues
        //that nobody uses anymore.
        Holder<Integer> cleanedQueues = new Holder<>(0);
        switch (MSG_MODEL) {
            case QUEUE:
                listQueueIds(null).stream().forEach(qId -> {
                    Long lastQueueUse = retrieve("jemo-queue-usage", qId, Long.class);
                    if (lastQueueUse == null || TimeUnit.MINUTES.convert(System.currentTimeMillis() - lastQueueUse, TimeUnit.MILLISECONDS) > 15) {
                        //if a queue was inactive for more than 15 minutes then delete it.
                        executeFunction((rt) -> {
                            CloudQueueClient cloudQueue = getQueueStorage();
                            CloudQueue queue = cloudQueue.getQueueReference(qId);
                            queue.downloadAttributes();
                            Jemo.log(Level.INFO, "[AZURE][CLOUDGC][%s] appears to be dead and contains [%d] messages to process it was last polled on [%s]",
                                    qId, queue.getApproximateMessageCount(), lastQueueUse == null ? "never" : new SimpleDateFormat("dd-MM-yyyy HH:MM:ss").format(new java.util.Date(lastQueueUse)));
                            queue.deleteIfExists();
                            return null;
                        }, this);
                        cleanedQueues.value++;
                    }
                });
                break;
        }
        Jemo.log(Level.INFO, "[AZURE][CLOUDGC] - Cloud Infrastructure Garbage Collection Running - Queues [%d]", cleanedQueues.value);
    }

    private CloudStorageAccount getCloudStorage() throws ClientProtocolException, IOException, URISyntaxException {
        if (cloudStorage == null) {
            String accessToken = getAuthToken();
            String subId = getSubscriptionId(accessToken);
            final Response response = Request.Post(AZURE_RESOURCE_PORTAL + "subscriptions/" + subId + "/resourceGroups/" + RESOURCE_GROUP + "/providers/Microsoft.Storage/storageAccounts/" + STORAGE_ACCOUNT + "/listKeys?api-version=2016-12-01")
                    .addHeader("Authorization", "Bearer " + accessToken)
                    .addHeader("x-ms-version", "2016-12-01").execute();
            String postResult = response.returnContent().asString(Charset.forName("UTF-8"));
            String authKey = Jemo.getValueFromJSON(postResult, "$.keys[0].value");

            cloudStorage = new CloudStorageAccount(new StorageCredentialsAccountAndKey(STORAGE_ACCOUNT, authKey), true);
        }

        return cloudStorage;
    }

    private CloudBlobClient getBlobStorage() throws ClientProtocolException, IOException, URISyntaxException {
        //POST /subscriptions/{subscriptionId}/resourceGroups/{resourceGroupName}/providers/Microsoft.Storage/storageAccounts/{accountName}/listKeys?api-version=2016-12-01
        if (blobStorage == null) {
            blobStorage = getCloudStorage().createCloudBlobClient();
        }

        return blobStorage;
    }

    private CloudQueueClient getQueueStorage() throws ClientProtocolException, IOException, URISyntaxException {
        if (queueStorage == null) {
            queueStorage = getCloudStorage().createCloudQueueClient();
        }

        return queueStorage;
    }

    private CloudBlobContainer getModuleContainer() throws ClientProtocolException, IOException, URISyntaxException, StorageException {
        return getStorageContainer(STORAGE_MODULE_CONTAINER);
    }

    private CloudBlobContainer getStorageContainer(String containerKey) throws ClientProtocolException, IOException, URISyntaxException, StorageException {
        CloudBlobContainer storageContainer = blobContainerMap.get(containerKey);
        if (storageContainer == null) {
            CloudBlobClient blobClient = getBlobStorage();
            storageContainer = blobClient.getContainerReference(containerKey);
            storageContainer.createIfNotExists();
            blobContainerMap.put(containerKey, storageContainer);
        }

        return storageContainer;
    }

    @Override
    public Set<String> listPlugins() {
        //this should work with Azure Blob storage
        return executeFunction(rt -> {
            HashSet<String> plugins = new HashSet<>();
            getModuleContainer().listBlobs().forEach(b -> {
                if (b instanceof CloudBlockBlob) {
                    CloudBlockBlob cb = CloudBlockBlob.class.cast(b);
                    if (cb.getName().endsWith(".jar")) {
                        plugins.add(cb.getName());
                    }
                }
            });
            return plugins;
        }, this);
    }

    @Override
    public void uploadModule(String pluginFile, byte[] pluginBytes) {
        executeFunction(rt -> {
            getModuleContainer().getBlockBlobReference(pluginFile).uploadFromByteArray(pluginBytes, 0, pluginBytes.length);
            return null;
        }, this);
    }

    @Override
    public void setModuleConfiguration(int pluginId, ModuleConfiguration config) {
        executeFunction(rt -> {
            Map<String, String> modConfig = getModuleConfiguration(pluginId);
            config.getParameters().forEach(p -> {
                switch (p.getOperation()) {
                    case delete:
                        modConfig.remove(p.getKey());
                        break;
                    case upsert:
                        modConfig.put(p.getKey(), p.getValue());
                        break;
                }
            });
            uploadModule(String.valueOf(pluginId) + "_configuration.json", AESCrypt.AESEncrypt(Jemo.toJSONString(modConfig), "UTF-8", ENCRYPTION_KEY));

            return null;
        }, this);
    }

    @Override
    public Map<String, String> getModuleConfiguration(int pluginId) {
        return executeFunction(rt -> {
            Map<String, String> config = new HashMap<>(); //we could very well store this on blob store
            CloudBlob configBlob = getModule(String.valueOf(pluginId) + "_configuration.json");
            if (configBlob != null) {
                //decrypt the configuration string
                String configData = new String(AESCrypt.AESDecrypt(configBlob.getData(), ENCRYPTION_KEY), "UTF-8");
                Map<String, String> savedConfig = Jemo.fromJSONString(Map.class, configData);
                if (!savedConfig.isEmpty()) {
                    config.putAll(savedConfig);
                }
            }
            return config;
        }, this);
    }

    @Override
    public List<String> listQueueIds(String location, boolean includeWorkQueues) {
        return executeFunction(rt -> {
            //GET /subscriptions/{subscriptionId}/resourceGroups/{resourceGroupName}/providers/Microsoft.EventHub/namespaces/{namespaceName}/eventhubs?api-version=2015-08-01
            List<String> queueList = new ArrayList<>();
            switch (MSG_MODEL) {
                case EVENTHUB:
                    String authKey = getAuthToken();
                    String subId = getSubscriptionId(authKey);
                    final Response response = Request.Get(AZURE_RESOURCE_PORTAL + "subscriptions/" + subId + "/resourceGroups/" + RESOURCE_GROUP + "/providers/Microsoft.EventHub/namespaces/" + EVENT_HUB_NAMESPACE + "/eventhubs?api-version=2015-08-01")
                            .addHeader("Authorization", "Bearer " + authKey)
                            .addHeader("x-ms-version", "2015-08-01").execute();
                    try {
                        queueList.addAll(getListFromJSON(response.returnContent().asString(Charset.forName("UTF-8")), "$.value[*].id"));
                    } catch (Exception e) {
                        Jemo.log(Level.FINE, "[AZURE][listQueueIds] EVENTHUB failure status code: " + response.returnResponse().getStatusLine() + ", body: " + response.returnResponse().getEntity().getContent());
                    }
                    return queueList.parallelStream().filter(qId -> {
                        String qName = qId.substring(qId.lastIndexOf('/') + 1).toUpperCase();
                        return (includeWorkQueues || !qName.contains("WORK-QUEUE")) && qName.indexOf(("jemo-" + (location == null ? "" : location)).toUpperCase()) == 0;
                    }).collect(toList());
                case QUEUE:
                    getQueueStorage().listQueues("jemo-").forEach(q -> {
                        if ((includeWorkQueues || !q.getName().toUpperCase().contains("WORK-QUEUE")) && q.getName().toUpperCase().indexOf(("jemo-" + (location == null ? "" : location)).toUpperCase()) == 0) {
                            queueList.add(q.getName());
                        }
                    });
                    return queueList;
            }
            return null;
        }, this);
    }

    @Override
    public String getDefaultCategory() {
        return STORAGE_MODULE_CONTAINER;
    }

    @Override
    public Stream<InputStream> readAll(String category, String path) {
        return executeFunction(rt -> {
            return StreamSupport.stream(getStorageContainer(category).listBlobs(path + "/").spliterator(), true)
                    .filter(i -> i instanceof CloudBlob || i instanceof CloudBlockBlob)
                    .map(i -> {
                        if (i instanceof CloudBlob) {
                            return CloudBlob.class.cast(i).getDataStream();
                        } else {
                            try {
                                return CloudBlockBlob.class.cast(i).openInputStream();
                            } catch (StorageException e) {
                                throw new RuntimeException(e);
                            }
                        }
                    }).parallel();
        }, this);
    }

    @Override
    public void remove(String category, String path, String key) {
        executeFunction(rt -> {
            getStorageContainer(category).getBlockBlobReference(path == null ? key : path + "/" + key).deleteIfExists();
            return null;
        }, this);
    }

    @Override
    public String getQueueName(String queueId) {
        return queueId.substring(queueId.lastIndexOf('/') + 1);
    }

    @Override
    public ValidationResult validateCredentials(Map<String, String> map) {
        final AzureCredentials azureCredentials = new AzureCredentials(map.get(TENANT_ID_VAR_NAME),
                map.get(CLIENT_ID_VAR_NAME),
                map.get(CLIENT_SECRET_VAR_NAME));
        return validateCredentials(azureCredentials);
    }

    private ValidationResult validateCredentials(AzureCredentials azureCredentials) {
        try {
            getAuthToken(azureCredentials);
            AZURE_CREDENTIALS = azureCredentials;
            return ValidationResult.SUCCESS;
        } catch (Throwable t) {
            Jemo.log(Level.FINE, "[AZURE][validateCredentials] User credentials validation failed:  %s", t.getMessage());
            return new ValidationResult(Collections.singletonList(t.getMessage()));
        }
    }

    @Override
    public ValidationResult validatePermissions() {
        if (lastAuthToken == null) {
            final ValidationResult validationResult = validateCredentials(AZURE_CREDENTIALS());
            if (!validationResult.isSuccess()) {
                return validationResult;
            }
        }

        try {
            HttpResponse authKeyResponse = Request.Post("https://login.microsoftonline.com/" + AZURE_CREDENTIALS().tenantId + "/oauth2/token")
                    .addHeader("Content-Type", "application/x-www-form-urlencoded")
                    .bodyForm(Form.form().add("grant_type", "client_credentials")
                            .add("client_id", AZURE_CREDENTIALS().clientId)
                            .add("client_secret", AZURE_CREDENTIALS().clientSecret)
                            .add("resource", "https://graph.windows.net/").build()).execute().returnResponse();

            Map<String, String> authResult = fromJSONString(Map.class, responseBody(authKeyResponse));
            String authKey = authResult.get("access_token");

            final HttpResponse principalObjectIdResponse = Request.Get(
                    "https://graph.windows.net/" + AZURE_CREDENTIALS().tenantId + "/servicePrincipals?$filter=servicePrincipalNames%2Fany%28c%3Ac%20eq%20%27" + AZURE_CREDENTIALS().clientId + "%27%29&api-version=1.6")
                    .addHeader("Authorization", "Bearer " + authKey)
                    .execute().returnResponse();
            final String principalServiceObjectId = getValueFromJSON(responseBody(principalObjectIdResponse), "value[0].objectId");

            HttpResponse roleAssignmentsResponse = sendRequestWithSubscription(GET, "/providers/Microsoft.Authorization/roleAssignments?$filter=principalId%20eq%20'" + principalServiceObjectId + "'&api-version=2018-01-01-preview", null);
            final List<String> assignedRoleDefinitionIds = getListFromJSON(responseBody(roleAssignmentsResponse), "$.value.*.properties.roleDefinitionId");
            final List<String> allowedActions = assignedRoleDefinitionIds.stream()
                    .flatMap(assignedRoleDefinitionId -> {
                        final HttpResponse roleDefinitionResponse = sendRequest(GET, AZURE_RESOURCE_PORTAL + assignedRoleDefinitionId.substring(1) + "?api-version=2015-07-01", null);
                        return getListFromJSON(responseBody(roleDefinitionResponse), "$.properties.permissions[0].actions").stream();
                    }).collect(Collectors.toList());
            final List<String> notPermittedActions = REQUIRED_ACTIONS.stream()
                    .filter(action -> !allowedActions.contains(action))
                    .collect(toList());
            if (!notPermittedActions.isEmpty()) {
                Jemo.log(Level.WARNING, "[AZURE][validatePermissions] The following required actions are not allowed to service principal: [%s]", notPermittedActions);
            }
            return new ValidationResult(notPermittedActions);
        } catch (Exception e) {
            return new ValidationResult(asList("Failed to validate permissions, exception message: " + e.getMessage()));
        }
    }

    @Override
    public void updateCredentials(Map<String, String> credentials) throws IOException {
        AZURE_CREDENTIALS = new AzureCredentials(credentials.get(TENANT_ID_VAR_NAME), credentials.get(CLIENT_ID_VAR_NAME), credentials.get(CLIENT_SECRET_VAR_NAME));

        final Path azureDirectory = azureDirectory();
        if (!Files.exists(azureDirectory)) {
            Files.createDirectory(azureDirectory);
        }

        final String content = "[default]\n" +
                "client=" + credentials.get(CLIENT_ID_VAR_NAME) + "\n" +
                "key=" + credentials.get(CLIENT_SECRET_VAR_NAME) + "\n" +
                "tenant=" + credentials.get(TENANT_ID_VAR_NAME);
        final Path credentialsFile = azureDirectory.resolve("credentials");
        Files.deleteIfExists(credentialsFile);
        Files.write(credentialsFile, content.getBytes(), CREATE);
    }

    private static Path azureDirectory() {
        final Path path = Util.pathUnderHomdeDir(".azure");
        if (!Files.exists(path)) {
            Util.B(path, Files::createDirectory);
        }
        return path;
    }

    @Override
    public void setRegion(String regionCode) throws IOException {
        REGION = regionCode;
        final String content = "region = " + regionCode + "\n";
        Files.write(azureDirectory().resolve("region"), content.getBytes(), CREATE);
    }

    @Override
    public String readInstanceTag(String s) {
        HttpResponse response = sendRequestWithSubscriptionAndResourceGroup(GET, "/providers/Microsoft.Compute/virtualMachines?api-version=2018-06-01", "");
        if (response.getStatusLine().getStatusCode() == 200) {
            Jemo.log(Level.FINE, "[AZURE][readInstanceTag] Could n [%s]", RESOURCE_GROUP);
        } else {
            throw new IllegalStateException("Could not list virtual machines for resource group [" + RESOURCE_GROUP + "] azure return value: " + responseBody(response));
        }

        try {
            return getValueFromJSON(responseBody(response), "$.value[0].tags.PARAM_SET_NAME");
        } catch (PathNotFoundException e) {
            return null;
        }
    }

    @Override
    public List<RegionInfo> getRegions() {
        return Arrays.asList(
                new RegionInfo("australiaeast", "Australia East"),
                new RegionInfo("canadacentral", "Canada Central"),
                new RegionInfo("canadaeast", "Canada East"),
                new RegionInfo("centralus", "Central US"),
                new RegionInfo("eastus", "East US"),
                new RegionInfo("eastus2", "East US 2"),
                new RegionInfo("japaneast", "Japan East"),
                new RegionInfo("northeurope", "North Europe"),
                new RegionInfo("southeastasia", "South East Asia"),
                new RegionInfo("southindia", "South India"),
                new RegionInfo("uksouth", "UK South"),
                new RegionInfo("ukwest", "UK West"),
                new RegionInfo("westeurope", "West Europe"),
                new RegionInfo("westus", "West US"),
                new RegionInfo("westus2", "West US 2")
        );
    }

    private static final Map<String, String> AKS_REGION_TO_LOG_WORKSPACE_REGION = new HashMap<String, String>() {{
        put("australiaeast", "australiaeast");

        put("canadacentral", "canadacentral");
        put("canadaeast", "canadacentral");

        put("centralus", "eastus");
        put("eastus", "eastus");
        put("eastus2", "eastus");
        put("westus", "westus2");
        put("westus2", "westus2");

        put("japaneast", "japaneast");

        put("northeurope", "northeurope");
        put("westeurope", "westeurope");

        put("southeastasia", "southeastasia");
        put("southindia", "centralindia");

        put("uksouth", "uksouth");
        put("ukwest", "uksouth");
    }};

    @Override
    public void resetLogConsoleHandler(Handler handler) {
        // Do nothing.
    }

    @Override
    public List<String> getExistingNetworks() {
        final HttpResponse response = sendRequestWithSubscriptionAndResourceGroup(GET, "/providers/Microsoft.Network/virtualNetworks?api-version=2018-10-01", null);
        final String responseBody = responseBody(response);
        return getListFromJSON(responseBody, "$.value.*.name");
    }

    @Override
    public List<String> getCustomerManagedPolicies() {
        return null;
    }

    @Override
    public ValidationResult validatePolicy(String s) {
        return null;
    }

    @Override
    public TerraformJobResponse install(String region, StringBuilder builder) throws IOException {
        final Path terraformDirPath = createInstallTerraformTemplates(region);
        final TerraformJob terraformJob = new TerraformJob(terraformDirPath.toString(), terraformDirPath.toString() + "/" + TFVARS_FILE_NAME).run(builder);
        Files.copy(Paths.get("terraform.tfstate"), terraformDirPath.resolve("terraform.tfstate"));
        return TerraformJobResponse.fromTerraformJob(terraformJob);
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

        if (region != null) {
            final String logWorkspaceLocation = getLogWorkSpaceLocationBasedOnRegion(region);
            final String varsFileContent = "terraform_user_client_id=\"" + AZURE_CREDENTIALS().clientId + "\"\n" +
                    "terraform_user_client_secret=\"" + AZURE_CREDENTIALS().clientSecret + "\"\n" +
                    "tenant_id=\"" + AZURE_CREDENTIALS().tenantId + "\"\n" +
                    "subscription_id=\"" + getSubscriptionId(getAuthToken()) + "\"\n" +
                    "region=\"" + region + "\"\n" +
                    "log-workspace-location=\"" + logWorkspaceLocation + "\"\n";
            Files.write(Paths.get(terraformDirPath.toString() + "/" + TFVARS_FILE_NAME), varsFileContent.getBytes(), CREATE);
        }

        copy(terraformDir, terraformDirPath, "install.tf", getClass());
        copy(terraformDir, terraformDirPath, "variables.tf", getClass());
        copy(terraformDir, terraformDirPath, "service_principal.tf", getClass());
        copy(terraformDir, terraformDirPath, "output.tf", getClass());
        return terraformDirPath;
    }

    /**
     * Regions offering the AKS service do not necessarily offer the Log Analytics service (see https://azure.microsoft.com/en-gb/pricing/details/monitor/),
     * therefore this method finds the closest possible region offering the Analytics service
     *
     * @return
     */
    @NotNull
    private String getLogWorkSpaceLocationBasedOnRegion(String region) {
        return AKS_REGION_TO_LOG_WORKSPACE_REGION.getOrDefault(region, "uksouth");
    }

    @Override
    public ClusterParams getClusterParameters() {
        return new ClusterParams(
                asList(
                        new ClusterParam("resource_group_name", RESOURCE_GROUP, "Name of the resource group"),
                        new ClusterParam("jemo_user_client_id", AZURE_CREDENTIALS().clientId, "The Jemo user client id"),
                        new ClusterParam("jemo_user_client_secret", AZURE_CREDENTIALS().clientSecret, "The Jemo user client secret"),
                        new ClusterParam("key_vault_name", KEY_VAULT, "The Jemo key vault name"),
                        new ClusterParam("aks_name", "jemo-cluster", "Name of the AKS cluster"),
                        new ClusterParam("aks_dns_prefix", "jemo", "Optional DNS prefix to use with hosted Kubernetes API server FQDN"),
                        new ClusterParam("aks_service_cidr", "10.0.0.0/16", "A CIDR notation IP range from which to assign service cluster IPs")
                ),
                asList(
                        new ClusterParam("aks_agent_os_disk_size", "30", "Disk size (in GB) to provision for each of the agent pool nodes"),
                        new ClusterParam("aks_agent_count", "2", "The number of agent nodes for the cluster"),
                        new ClusterParam("aks_agent_vm_size", "Standard_D1_v2", "The size of the Virtual Machine"),
                        new ClusterParam("aks_dns_service_ip", "10.0.0.10", "Containers DNS server IP address"),
                        new ClusterParam("aks_docker_bridge_cidr", "172.17.0.1/16", "A CIDR notation IP for Docker bridge")
                ),
                asList(
                        new ClusterParam("virtual_network_name", "jemo-virtual-network", "Virtual network name"),
                        new ClusterParam("virtual_network_address_prefix", "15.0.0.0/8", "Containers DNS server IP address"),
                        new ClusterParam("aks_subnet_name", "jemo-subnet", "AKS Subnet Name"),
                        new ClusterParam("aks_subnet_address_prefix", "15.0.0.0/16", "Containers DNS server IP address"),
                        new ClusterParam("app_gateway_subnet_name", "appgwsubnet", "The App Gateway Subnet Name"),
                        new ClusterParam("app_gateway_subnet_address_prefix", "15.1.0.0/16", "Containers DNS server IP address"),
                        new ClusterParam("app_gateway_name", "jemo-app-gateway", "Name of the Application Gateway"),
                        new ClusterParam("app_gateway_sku", "Standard_v2", "Name of the Application Gateway SKU"),
                        new ClusterParam("app_gateway_tier", "Standard_v2", "Tier of the Application Gateway SKU")
                )
        );
    }

    @Override
    public void deleteKubernetesResources(StringBuilder builder) throws IOException {
        runProcess(builder, new String[]{
                "/bin/sh", "-c", "echo \"$(terraform output kube_config)\" > ~/.kube/config ; " +
                "kubectl delete statefulset jemo ; " +
                "kubectl delete svc jemo"
        });
    }

    @Override
    public String getCspLabel() {
        return "azure";
    }

    @Override
    public HashMap<String, String> getCredentialsFromTerraformResult(TerraformJob.TerraformResult terraformResult) {
        return new HashMap<String, String>() {{
            put(CLIENT_ID_VAR_NAME, terraformResult.getOutput("jemo_user_client_id"));
            put(CLIENT_SECRET_VAR_NAME, terraformResult.getOutput("jemo_user_client_secret"));
            put(TENANT_ID_VAR_NAME, AZURE_CREDENTIALS().tenantId);
        }};
    }

    @Override
    public ClusterCreationResponse createCluster(SetupParams setupParams, StringBuilder builder) throws IOException, ApiException {
        setupParams.parameters().put("terraform_user_client_id", AZURE_CREDENTIALS().clientId);
        setupParams.parameters().put("terraform_user_client_secret", AZURE_CREDENTIALS().clientSecret);
        final Path terraformDirPath = createClusterTerraformTemplates(setupParams);
        final TerraformJob terraformJob = new TerraformJob(terraformDirPath.toString(), terraformDirPath.toString() + "/" + TFVARS_FILE_NAME).run(builder);
        final Path source = Paths.get("terraform.tfstate");
        if (Files.exists(source)) {
            Files.copy(source, terraformDirPath.resolve("terraform.tfstate"));
        }

        final String kubernetesDir = getTerraformClusterDir() + "/kubernetes/";
        String[] command = new String[]{
                "/bin/sh", "-c", "echo \"$(terraform output kube_config)\" > ~/.kube/config ; " +
                "kubectl create -f https://raw.githubusercontent.com/Azure/kubernetes-keyvault-flexvol/master/deployment/kv-flexvol-installer.yaml ; " +
                "kubectl create secret generic kvcreds --from-literal clientid='" + setupParams.parameters().get("jemo_user_client_id") + "' --from-literal clientsecret='" + setupParams.parameters().get("jemo_user_client_secret") + "' --type=azure/kv ; " +
                "kubectl create -f " + kubernetesDir + "/jemo-statefulset.yaml ; " +
                "kubectl create -f " + kubernetesDir + "/jemo-svc.yaml ; " +
                "kubectl rollout status statefulset jemo"
        };

        runProcess(builder, command);

        final String clusterUrl = terraformJob.getResult().getOutput("host");
        final String token = terraformJob.getResult().getOutput("cluster_password");
        final ApiClient apiClient = Config.fromToken(clusterUrl, token, false);
        Configuration.setDefaultApiClient(apiClient);
        final CoreV1Api coreV1Api = new CoreV1Api();
        final String loadBalancerUrl = getLoadBalancerUrl(coreV1Api);
        return ClusterCreationResponse.fromTerraformJob(terraformJob).setLoadBalancerUrl(loadBalancerUrl);
    }

    @Override
    public Path createClusterTerraformTemplates(SetupParams setupParams) throws IOException {
        setupParams.parameters().put("tenant_id", AZURE_CREDENTIALS().tenantId);
        setupParams.parameters().put("subscription_id", subscriptionId);
        final Path terraformDirPath = prepareTerraformFiles(setupParams);

        final String subscriptionId = getSubscriptionId(getAuthToken());
        prepareClusterCreation(setupParams);

        final Path kubernetesDirPath = terraformDirPath.resolve("kubernetes");
        Files.createDirectories(kubernetesDirPath);

        final String kubernetesSourceDir = getTerraformClusterDir() + "kubernetes/";
        copy(kubernetesSourceDir, kubernetesDirPath, "jemo-svc.yaml", getClass());

        final String replicas = setupParams.parameters().get("aks_agent_count");
        applyTemplate(kubernetesSourceDir, kubernetesDirPath, "jemo-statefulset.yaml", getClass(),
                x -> x.replaceAll("_REPLICAS_", replicas)
                        .replaceAll("_RESOURCE_GROUP_", RESOURCE_GROUP)
                        .replaceAll("_EVENT_HUB_", EVENT_HUB_NAMESPACE)
                        .replaceAll("_DB_", DATABASE_ACCOUNT)
                        .replaceAll("_STORAGE_", STORAGE_ACCOUNT)
                        .replaceAll("_LOG_WORKSPACE_", LOG_WORKSPACE)
                        .replaceAll("_KEYVAULT_", KEY_VAULT)
                        .replaceAll("_SUBSCRIPTION_ID_", subscriptionId)
                        .replaceAll("_TENANT_ID_", AZURE_CREDENTIALS().tenantId)
                        .replaceAll("_MSG_MODEL_", MSG_MODEL.name())
        );

        return terraformDirPath;
    }

    @Override
    public AdminUserCreationInstructions getAdminUserCreationInstructions() {
        return new AdminUserCreationInstructions(
                "Jemo setup requires a service principal (the \"terraform-user\") with \"Owner\" role to run terraform with. This service principal is used to create " +
                        "another service principal (the \"jemo-user\") with which Jemo pods run on the cluster worker nodes. " +
                        "Therefore the \"terraform-user\" must have permissions to both \"Read and write all applications\" and \"Sign in and read user profile\" within the Windows Azure Active Directory API." +
                        "Please install the azure cli, see https://docs.microsoft.com/en-us/cli/azure/install-azure-cli?view=azure-cli-latest",
                asList(
                        "az ad sp create-for-rbac -n \"terraform-user\" --role Owner",
                        "az ad app permission add --id http://terraform-user --api 00000002-0000-0000-c000-000000000000 --api-permissions 1cda74f2-2616-4834-b122-5cb1b07f8a59=Role 311a71cc-e848-46a1-bdf8-97ff7156d8e6=Scope",
                        "Then, open the console and navigate to \"Azure Active Directory\" -> \"App registrations (Preview)\" -> \"terraform-user\"\n" +
                                "-> \"API permissions\" -> \"Grant admin consent for Default Directory\" -> \"Yes\""
                )
        );
    }

    @Override
    public List<InstallProperty> getInstallProperties() {
        return asList(
                new InstallProperty().name(PROP_RESOURCEGROUP).description("The resource group name"),
                new InstallProperty().name(PROP_EVENTHUB).description("The event hub namespace name"),
                new InstallProperty().name(PROP_DB).description("The database account name"),
                new InstallProperty().name(PROP_STORAGE).description("The storage account name"),
                new InstallProperty().name(PROP_LOG_WORKSPACE).description("The log workspace name"),
                new InstallProperty().name(PROP_KEYVAULT).description("The key vault name"),
                new InstallProperty().name(PROP_MSG_MODEL).description("The Messaging Model").range(asList(QUEUE.name(), EVENTHUB.name())).value(QUEUE.name())
        );
    }

    @Override
    public void setInstallProperties(Map<String, String> properties) {
        RESOURCE_GROUP = properties.getOrDefault(PROP_RESOURCEGROUP, RESOURCE_GROUP);
        EVENT_HUB_NAMESPACE = properties.getOrDefault(PROP_EVENTHUB, EVENT_HUB_NAMESPACE);
        DATABASE_ACCOUNT = properties.getOrDefault(PROP_DB, DATABASE_ACCOUNT);
        STORAGE_ACCOUNT = properties.getOrDefault(PROP_STORAGE, STORAGE_ACCOUNT);
        LOG_WORKSPACE = properties.getOrDefault(PROP_LOG_WORKSPACE, LOG_WORKSPACE);
        KEY_VAULT = properties.getOrDefault(PROP_KEYVAULT, KEY_VAULT);
        MSG_MODEL = MESSAGE_MODEL.valueOf(properties.getOrDefault(PROP_MSG_MODEL, MSG_MODEL.name()));

        Properties propertiesFromFile = readPropertiesFile();
        if (propertiesFromFile == null) {
            propertiesFromFile = new Properties();
        }

        propertiesFromFile.setProperty(PROP_RESOURCEGROUP, RESOURCE_GROUP);
        propertiesFromFile.setProperty(PROP_EVENTHUB, EVENT_HUB_NAMESPACE);
        propertiesFromFile.setProperty(PROP_DB, DATABASE_ACCOUNT);
        propertiesFromFile.setProperty(PROP_STORAGE, STORAGE_ACCOUNT);
        propertiesFromFile.setProperty(PROP_LOG_WORKSPACE, LOG_WORKSPACE);
        propertiesFromFile.setProperty(PROP_KEYVAULT, KEY_VAULT);
        propertiesFromFile.setProperty(PROP_MSG_MODEL, MSG_MODEL.name());
        storePropertiesFile(propertiesFromFile);
    }

    @NotNull
    private Path prepareTerraformFiles(SetupParams setupParams) throws IOException {
        final String terraformDir = getTerraformClusterDir();
        final Path terraformDirPath = Paths.get(terraformDir);
        if (Files.exists(terraformDirPath)) {
            Util.deleteDirectory(terraformDirPath.toFile());
        }
        Files.createDirectories(terraformDirPath);

        final String existingNetworkName = setupParams.parameters().get("existing-network-name");
        final String dependsOnVn = existingNetworkName == null ? "  depends_on           = [\"azurerm_virtual_network.vn\"]" : "";
        final String dependsOnVnAppend = existingNetworkName == null ? ", \"azurerm_virtual_network.vn\"" : "";

        copy(terraformDir, terraformDirPath, "README.txt", getClass());
        copy(terraformDir, terraformDirPath, "cluster.tf", getClass());
        copy(terraformDir, terraformDirPath, "identity.tf", getClass());
        copy(terraformDir, terraformDirPath, "locals.tf", getClass());
        applyTemplate(terraformDir, terraformDirPath, "main.tf", getClass(), x -> x.replaceAll("_DEPENDS_ON_VN_", dependsOnVn));
        applyTemplate(terraformDir, terraformDirPath, "gateway.tf", getClass(), x -> x.replaceAll("_DEPENDS_ON_VN_", dependsOnVnAppend));
        copy(terraformDir, terraformDirPath, "output.tf", getClass());
        copy(terraformDir, terraformDirPath, "roles.tf", getClass());
        copy(terraformDir, terraformDirPath, "variables.tf", getClass());

        if (existingNetworkName == null) {
            copy(terraformDir, terraformDirPath, "vn.tf", getClass());
        } else {
            setupParams.parameters().put("virtual_network_name", existingNetworkName);
        }

        setupParams.parameters().put("location", REGION);
        setupParams.parameters().put("resource_group_name", RESOURCE_GROUP);

        final String varsFileContent = setupParams.parameters().keySet().stream()
                .filter(key -> !key.equals("existing-network-name") && !key.equals("policy-id") && !key.equals("containersPerParamSet"))
                .map(key -> key + "=\"" + setupParams.parameters().get(key) + "\"")
                .collect(Collectors.joining("\n"));

        Files.write(Paths.get(terraformDirPath.toString() + "/" + TFVARS_FILE_NAME), varsFileContent.getBytes(), CREATE);
        return terraformDirPath;
    }

    @Override
    public void removePluginFiles(String pluginJarFileName) {
        remove(null, pluginJarFileName);
        remove(null, pluginJarFileName + ".installed");
        remove(null, pluginJarFileName + ".modulelist");
        delete(getDefaultCategory(), pluginJarFileName + ".classlist.moduledata");
        delete(getDefaultCategory(), pluginJarFileName + ".crc32.moduledata");
    }
}
