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
package org.eclipse.jemo.aws;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.DefaultRequest;
import com.amazonaws.Request;
import com.amazonaws.auth.*;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.http.HttpMethodName;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.*;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.model.*;
import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;
import com.amazonaws.services.ec2.model.DescribeTagsRequest;
import com.amazonaws.services.ec2.model.DescribeTagsResult;
import com.amazonaws.services.ec2.model.Filter;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.identitymanagement.model.*;
import com.amazonaws.services.logs.AWSLogsAsync;
import com.amazonaws.services.logs.AWSLogsAsyncClientBuilder;
import com.amazonaws.services.logs.model.*;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.securitytoken.AWSSecurityTokenService;
import com.amazonaws.services.securitytoken.AWSSecurityTokenServiceClientBuilder;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityRequest;
import com.amazonaws.services.securitytoken.model.GetCallerIdentityResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.*;
import com.amazonaws.util.EC2MetadataUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.io.BaseEncoding;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.AppsV1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import io.kubernetes.client.util.Config;
import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.internal.model.QueueDoesNotExistException;
import org.eclipse.jemo.internal.model.*;
import org.eclipse.jemo.sys.ClusterParams;
import org.eclipse.jemo.sys.ClusterParams.ClusterParam;
import org.eclipse.jemo.sys.JemoRuntimeSetup;
import org.eclipse.jemo.sys.internal.TerraformJob;
import org.eclipse.jemo.sys.internal.TerraformJob.TerraformResult;
import org.eclipse.jemo.sys.internal.Util;

import javax.xml.ws.Holder;
import java.io.*;
import java.math.BigInteger;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static com.amazonaws.services.s3.model.Region.fromValue;
import static java.lang.Thread.sleep;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jemo.api.JemoParameter.*;
import static org.eclipse.jemo.sys.internal.Util.*;

/**
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 * @author Yannis Theocharis "ytheohar@gmail.com"
 */
public class AmazonAWSRuntime implements CloudRuntime {

    private static final String ALLOWED = "allowed";
    public static final String AWS_ACCESS_KEY_ID = "aws_access_key_id";
    public static final String AWS_SECRET_ACCESS_KEY = "aws_secret_access_key";
    private static AWSCredentialsProvider AWS_CREDENTIALS_PROVIDER = null;
    private static final ExecutorService EVENT_PROCESSOR = Executors.newCachedThreadPool();
    private static final String[] DYNAMO_SYSTEM_TABLES = new String[]{"eclipse_jemo_module_configuration", "eclipse_jemo_security_groups", "eclipse_jemo_security_users", "eclipse_jemo_modules"};
    public static final String AWS_REGION_PROP = "ECLIPSE_JEMO_AWS_REGION";
    private static String AWSREGION;

    private String arn;
    private static final String[] ACTION_NAMES = new String[]{

            // DynamoDB actions
            "dynamodb:BatchWriteItem",
            "dynamodb:CreateTable",
            "dynamodb:DeleteTable",
            "dynamodb:ListTables",
            "dynamodb:DescribeTable",
            "dynamodb:Scan",
            "dynamodb:Query",
            "dynamodb:GetItem",

            // S3 actions
            "s3:AbortMultipartUpload",
            "s3:CreateBucket",
            "s3:DeleteObject",
            "s3:GetObject",
            "s3:ListBucketMultipartUploads",
            "s3:ListBucket",
            "s3:PutObject",

            // SQS actions
            "sqs:CreateQueue",
            "sqs:DeleteMessage",
            "sqs:DeleteQueue",
            "sqs:GetQueueUrl",
            "sqs:ListQueues",
            "sqs:SendMessage",
            "sqs:SetQueueAttributes",

            // AmazonSQSAsync
            "sqs:ReceiveMessage",

            // AWSLogsAsync
            "logs:CreateLogGroup",
            "logs:CreateLogStream",
            "logs:DescribeLogStreams",
            "logs:PutLogEvents",

            // EC2
            "ec2:DescribeTags",
            "ec2:DescribeVpcs",

            // IAM
            "iam:GetUser",
            "iam:SimulatePrincipalPolicy",
            "iam:GetRole",
            "iam:ListPolicies"
    };

    private static final AWSCredentialsProvider AWS_CREDENTIALS() {
        if (AWS_CREDENTIALS_PROVIDER == null) {
            if (System.getProperty("aws.accessKeyId") != null && System.getProperty("aws.secretKey") != null) {
                LOG(Level.FINE, "aws.accessKeyId found");
                AWS_CREDENTIALS_PROVIDER = new SystemPropertiesCredentialsProvider();
            } else {
                LOG(Level.FINE, "Checking if the credentials file exists.");
                AWS_CREDENTIALS_PROVIDER = readCredentialsFromFile();
                if (AWS_CREDENTIALS_PROVIDER == null) {
                    LOG(Level.FINE, "Could not find the aws credentials file, using the EC2ContainerCredentialsProviderWrapper instead.");
                    AWS_CREDENTIALS_PROVIDER = new EC2ContainerCredentialsProviderWrapper();
                } else {
                    AWSREGION = readRegionFromFile();
                    LOG(Level.FINE, "Credentials read from the aws credentials file.");
                }
            }
        }

        return AWS_CREDENTIALS_PROVIDER;
    }

    private static AWSStaticCredentialsProvider readCredentialsFromFile() {
        final String credentialsFilePath = awsDirectory().toString() + File.separator + "credentials";
        if (!Files.exists(Paths.get(credentialsFilePath))) {
            return null;
        }

        try (FileInputStream stream = new FileInputStream(credentialsFilePath)) {
            final Properties properties = new Properties();
            properties.load(stream);
            final String accessKey = properties.getProperty(AWS_ACCESS_KEY_ID);
            final String secretKey = properties.getProperty(AWS_SECRET_ACCESS_KEY);
            return new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        } catch (IOException e) {
            LOG(Level.WARNING, "[%s] I was unable to read the credentials from %s because of the error %s", AmazonAWSRuntime.class.getSimpleName(), credentialsFilePath, e.getMessage());
        }
        return null;
    }

    private static String readRegionFromFile() {
        final Path credentialsFilePath = awsDirectory();
        if (!Files.exists(credentialsFilePath)) {
            return null;
        }

        try (FileInputStream stream = new FileInputStream(credentialsFilePath.resolve("config").toFile())) {
            final Properties properties = new Properties();
            properties.load(stream);
            return properties.getProperty("region");
        } catch (IOException e) {
            Jemo.log(Level.WARNING, "[AZURE][readCredentialsFromFile] I was unable to read the credentials from %s because of the error %s", credentialsFilePath, e.getMessage());
            return null;
        }
    }

    private static DynamoDB _sys_dynamoDb = null;

    @Override
    public void deleteNoSQL(String tableName, SystemDBObject... data) {
        TableWriteItems writeJob = new TableWriteItems(tableName);
        asList(data).forEach(obj -> writeJob.addPrimaryKeyToDelete(new PrimaryKey("id", obj.getId())));
        getDynamoDB(getTableRegion(tableName)).batchWriteItem(writeJob);
    }

    @Override
    public void dropNoSQLTable(String tableName) {
        DeleteTableResult deleteTable = getDynamoDB(getTableRegion(tableName)).getTable(tableName).delete();
        //we should wait for the table to disappear before returning.
        while (hasNoSQLTable(tableName)) {
            try {
                sleep(100);
            } catch (InterruptedException irrEx) {
            }
        }
    }

    @Override
    public String getDefaultCategory() {
        return S3_PLUGIN_BUCKET();
    }

    @Override
    public Stream<InputStream> readAll(String category, String path) {
        final String bucketName = buildBucketName(category);
        final AmazonS3 s3 = getS3(getRegionForS3Bucket(bucketName));
        if (s3.doesBucketExistV2(bucketName)) {
            return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<InputStream>() {

                ListObjectsV2Result result = null;
                InputStream currentStream = null;
                Iterator<S3ObjectSummary> currentSummaryList = null;

                @Override
                public synchronized boolean hasNext() {
                    if (currentStream == null) {
                        currentStream = nextStream();
                    }

                    return currentStream != null;
                }

                @Override
                public synchronized InputStream next() {
                    if (currentStream == null) {
                        currentStream = nextStream();
                    }
                    try {
                        InputStream retval = currentStream;
                        return retval;
                    } finally {
                        currentStream = null;
                    }
                }

                private synchronized InputStream nextStream() {
                    if (currentSummaryList != null && currentSummaryList.hasNext()) {
                        S3ObjectSummary objSummary = currentSummaryList.next();
                        return s3.getObject(new GetObjectRequest(objSummary.getBucketName(), objSummary.getKey())).getObjectContent();
                    } else if (result == null) {
                        result = s3.listObjectsV2(new ListObjectsV2Request()
                                .withBucketName(bucketName)
                                .withPrefix(path));
                        currentSummaryList = result.getObjectSummaries().iterator();
                        return nextStream();
                    } else if (result.getNextContinuationToken() != null) {
                        result = s3.listObjectsV2(new ListObjectsV2Request()
                                .withBucketName(bucketName)
                                .withPrefix(path)
                                .withContinuationToken(result.getNextContinuationToken()));
                        currentSummaryList = result.getObjectSummaries().iterator();
                        return nextStream();
                    }

                    return null;
                }
            }, Spliterator.IMMUTABLE), true);
        }

        return Stream.empty();
    }

    public static class S3ObjectInputStream extends InputStream {
        private S3Object obj = null;
        private InputStream objInputStream = null;

        public S3ObjectInputStream(S3Object obj) {
            this.obj = obj;
        }

        @Override
        public int read() throws IOException {
            if (objInputStream == null) {
                objInputStream = this.obj.getObjectContent();
            }
            return objInputStream.read();
        }

        @Override
        public void close() throws IOException {
            super.close();
            obj.close();
        }

        @Override
        protected void finalize() throws Throwable {
            super.finalize(); //To change body of generated methods, choose Tools | Templates.
            close();
        }
    }

    private static synchronized <T extends Object> T buildClient(Class<T> typeToBuild, AwsClientBuilder<? extends AwsClientBuilder, T> builder) {
        return buildClient(typeToBuild, builder, AWSREGION);
    }

    private static synchronized <T extends Object> T buildClient(Class<T> typeToBuild, AwsClientBuilder<? extends AwsClientBuilder, T> builder, String region) {
        return buildClient(typeToBuild, builder, region, AWS_CREDENTIALS());
    }

    private static synchronized <T extends Object> T buildClient(Class<T> typeToBuild, AwsClientBuilder<? extends AwsClientBuilder, T> builder, String region, AWSCredentialsProvider credentialsProvider) {
        builder.setClientConfiguration(getClientConfiguration());
        builder.setRegion(region);
        builder.setCredentials(credentialsProvider);
        return builder.build();
    }

    private AmazonS3 s3Client = null;
    private AWSLogsAsync awsLog = null;
    private AmazonSQS sqs_client = null;
    private AmazonSQSAsync sqs_async_client = null;
    private AmazonIdentityManagement iam_client = null;
    private AmazonEC2 ec2_client;
    private String AWS_ACCOUNT_ID;
    private String SQS_DATA_BUCKET;
    private ScheduledExecutorService LOG_SCHEDULER = Executors.newScheduledThreadPool(1); //make sure that only one scheduled action is run at once.
    private boolean pluginBucketInitialized = false;
    private String logSequenceToken = null;
    private static final Logger LOG = Logger.getLogger(AmazonAWSRuntime.class.getSimpleName());
    private boolean isInitialized = false;
    private String S3_PLUGIN_BUCKET;

    public AmazonAWSRuntime() {
        AWSREGION = Util.readParameterFromJvmOrEnv(AWS_REGION_PROP);
    }

    @Override
    public void start(AbstractJemo jemoServer) {
        if (isInitialized) {
            return;
        }

        long start = System.currentTimeMillis();
        String globalQueue = defineQueue(Jemo.GLOBAL_QUEUE_NAME);
        String AWSAccountId = globalQueue.substring(0, globalQueue.lastIndexOf('/'));
        AWS_ACCOUNT_ID = AWSAccountId.substring(AWSAccountId.lastIndexOf('/') + 1);
        S3_PLUGIN_BUCKET = "jemopluginlib-" + AWS_ACCOUNT_ID;
        SQS_DATA_BUCKET = "jemomsgpayload-" + AWS_ACCOUNT_ID;

        //lets initialise the logger for this runtime
        LOG.setUseParentHandlers(false);
        if (jemoServer != null) {
            LOG.addHandler(jemoServer.getConsoleHandler());
            LOG.setLevel(jemoServer.getConsoleHandler().getLevel());
        }
        LOG.info(String.format("Discovered AWS Account Id as %s - initialized in %d (ms)", AWS_ACCOUNT_ID, System.currentTimeMillis() - start));
        isInitialized = true;
    }

    private static Path awsDirectory() {
        return Util.pathUnderHomdeDir(".aws");
    }

    @Override
    public void updateCredentials(Map<String, String> credentials) throws IOException {
        AWS_CREDENTIALS_PROVIDER = new AWSStaticCredentialsProvider(new BasicAWSCredentials(credentials.get(AWS_ACCESS_KEY_ID), credentials.get(AWS_SECRET_ACCESS_KEY)));

        Path awsDirectory = awsDirectory();
        if (!Files.exists(awsDirectory)) {
            Files.createDirectory(awsDirectory);
        }

        final String content =
                "[default]\n" +
                        AWS_ACCESS_KEY_ID + "=" + credentials.get(AWS_ACCESS_KEY_ID) + "\n" +
                        AWS_SECRET_ACCESS_KEY + "=" + credentials.get(AWS_SECRET_ACCESS_KEY) + "\n";
        final Path credentialsFile = awsDirectory.resolve("credentials");
        Files.deleteIfExists(credentialsFile);
        Files.write(credentialsFile, content.getBytes(), CREATE);
    }

    private synchronized AmazonSQS getSQS() {
        if (sqs_client == null) {
            sqs_client = buildClient(AmazonSQS.class, AmazonSQSClientBuilder.standard());
        }

        return sqs_client;
    }

    private synchronized AmazonSQSAsync getSQSAsync() {
        if (sqs_async_client == null) {
            sqs_async_client = buildClient(AmazonSQSAsync.class, AmazonSQSAsyncClientBuilder.standard());
        }

        return sqs_async_client;
    }

    private synchronized AmazonIdentityManagement getIAM() {
        if (iam_client == null) {
            iam_client = buildClient(AmazonIdentityManagement.class, AmazonIdentityManagementClientBuilder.standard());
        }

        return iam_client;
    }

    private synchronized AmazonEC2 getEC2() {
        if (ec2_client == null) {
            ec2_client = buildClient(AmazonEC2.class, AmazonEC2ClientBuilder.standard());
        }

        return ec2_client;
    }

    private synchronized String S3_PLUGIN_BUCKET() {
        if (!pluginBucketInitialized) {
            //we need to figure out of a bucket with the default name exists.
            String pluginBucketRegion = AbstractJemo.executeFailsafe(x -> getS3().headBucket(new HeadBucketRequest(S3_PLUGIN_BUCKET)).getBucketRegion(), null);
            if (pluginBucketRegion == null) { //we could not find the default bucket.
                AbstractJemo.executeFailsafe(x -> {
                    // Surprisingly enough, the com.amazonaws.services.s3.model.Region.US_Standard enum value has a null label, instead of "us-east-1".
                    // Therefore the overloaded 'createBucket' method that takes a Region object rather than String, should be called.
                    return getS3().createBucket(new CreateBucketRequest(S3_PLUGIN_BUCKET, fromValue(AWSREGION)));
                }, null);
                pluginBucketRegion = AWSREGION;
            }
            BUCKET_PRESENCE_CACHE.add(S3_PLUGIN_BUCKET);
            S3_BUCKET_REGION_MAP.put(S3_PLUGIN_BUCKET, pluginBucketRegion);
            pluginBucketInitialized = true;
        }

        return S3_PLUGIN_BUCKET;
    }

    private final AtomicBoolean CLOUDWATCH_INITIALIZED = new AtomicBoolean(false);

    private void initializeCloudwatchLogging() {
        if (CLOUDWATCH_INITIALIZED.compareAndSet(false, true)) {
            //we need to create a log stream in the aws environment for all of the instances.
            if (awsLog == null) {
                awsLog = buildClient(AWSLogsAsync.class, AWSLogsAsyncClientBuilder.standard());
            }
            AbstractJemo.executeFailsafe(awsLog::createLogGroup, new CreateLogGroupRequest("ECLIPSE-JEMO"));
            AbstractJemo.executeFailsafe(awsLog::createLogStream, new CreateLogStreamRequest("ECLIPSE-JEMO", "ECLIPSE-JEMO"));

            try {
                logSequenceToken = awsLog.describeLogStreams(new DescribeLogStreamsRequest("ECLIPSE-JEMO")).getLogStreams().iterator().next().getUploadSequenceToken();
            } catch (AWSLogsException logEx) {
                LOG(Level.WARNING, "[%s] I was unable to get the log sequence token because of the error %s", getClass().getSimpleName(), logEx.getMessage());
            }
        }
    }

    private static final void LOG(Level logLevel, String message, Object... args) {
        LOG.log(logLevel, message, args);
    }

    private DynamoDB getDynamoDB(String region) {
        if (region == null || region.equals(AWSREGION)) {
            if (_sys_dynamoDb == null) {
                _sys_dynamoDb = new DynamoDB(buildClient(AmazonDynamoDB.class, AmazonDynamoDBClientBuilder.standard(), region));
            }
            return _sys_dynamoDb;
        }

        return new DynamoDB(buildClient(AmazonDynamoDB.class, AmazonDynamoDBClientBuilder.standard(), region));
    }

    private static ClientConfiguration getClientConfiguration() {
        ClientConfiguration clientConfig = new ClientConfiguration();
        clientConfig.setClientExecutionTimeout(25000); //25 seconds is the client execution timeout (this will allow for long polling)
        clientConfig.setConnectionTTL(TimeUnit.MINUTES.toMillis(5)); //live for 5 minutes
        clientConfig.setConnectionTimeout(1000); //1 sec timeout for connections
        clientConfig.setMaxConsecutiveRetriesBeforeThrottling(10);
        clientConfig.setMaxConnections(500); //raise the maximum number of connections
        clientConfig.setRequestTimeout(30000); //30 seconds execution timeout

        return clientConfig;
    }

    private AmazonS3 getS3() {
        if (s3Client == null) {
            s3Client = buildClient(AmazonS3.class, AmazonS3ClientBuilder.standard());
        }

        return s3Client;
    }

    private AmazonS3 getS3(String region) {
        if (region == null) {
            return getS3();
        }
        return buildClient(AmazonS3.class, AmazonS3ClientBuilder.standard(), region);
    }

    private ReceiveMessageRequest createReceiveRequest(String queueUrl) {
        ReceiveMessageRequest req = new ReceiveMessageRequest(queueUrl);
        req.setMaxNumberOfMessages(10); //process 10 messages at the same time.
        req.setWaitTimeSeconds(20); //wait 20 seconds for messages
        req.setVisibilityTimeout(5); //any message we retrieve will not be visible to other workers for 5 seconds, we will however delete it immediately
        return req;
    }

    @Override
    public String defineQueue(String queueName) {
        //this will set all queues to have a retention period of 4 hours maximum
        String queueId = getQueueId(queueName);
        if (queueId == null) {
            CreateQueueResult instanceQueueResult = getSQS().createQueue(new CreateQueueRequest(queueName).addAttributesEntry(QueueAttributeName.MessageRetentionPeriod.name(), String.valueOf(TimeUnit.HOURS.toSeconds(4))));
            queueId = instanceQueueResult.getQueueUrl();
        } else {
            getSQS().setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueId).addAttributesEntry(QueueAttributeName.MessageRetentionPeriod.name(), String.valueOf(TimeUnit.HOURS.toSeconds(4))));
        }

        return queueId;
    }

    @Override
    public void storeModuleList(String moduleJar, List<String> moduleList) throws Throwable {
        getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).putObject(S3_PLUGIN_BUCKET(), moduleJar + ".modulelist", Jemo.toJSONString(moduleList));
    }

    @Override
    public List<String> getModuleList(String moduleJar) throws Throwable {
        try {
            return Jemo.fromJSONArray(String.class, Jemo.toString(getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).getObject(S3_PLUGIN_BUCKET(), moduleJar + ".modulelist").getObjectContent()));
        } catch (AmazonClientException amzClEx) {
        }
        return null;
    }

    @Override
    public CloudBlob getModule(String moduleJar) throws IOException {
        //this will eventually kill us because we need to make sure we close the s3obj reference.
        try {
            S3Object s3obj = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).getObject(S3_PLUGIN_BUCKET(), moduleJar);
            return new CloudBlob(moduleJar, s3obj.getObjectMetadata().getLastModified().getTime(), s3obj.getObjectMetadata().getContentLength(), new S3ObjectInputStream(s3obj));
        } catch (AmazonS3Exception s3Ex) {
            if (s3Ex.getStatusCode() == 404) {
                LOG(Level.INFO, "[%s][%s] the module could not be retrieved because it was not found in the plugin bucket %s", getClass().getSimpleName(), moduleJar, S3_PLUGIN_BUCKET());
                return null;
            } else {
                throw s3Ex;
            }
        }
    }

    @Override
    public Long getModuleInstallDate(String moduleJar) throws IOException {
        try {
            return new Long(getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).getObjectAsString(S3_PLUGIN_BUCKET(), moduleJar + ".installed")); //this could fail because the module may have never been installed.
        } catch (AmazonClientException awsEx) {
            if (!awsEx.getMessage().contains("Status Code: 404;")) {
                throw awsEx;
            }

            return null;
        }
    }

    @Override
    public void setModuleInstallDate(String moduleJar, long installDate) throws IOException {
        getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).putObject(S3_PLUGIN_BUCKET(), moduleJar + ".installed", String.valueOf(installDate)); //post an installation reference to s3.
    }

    @Override
    public void log(List<CloudLogEvent> eventList) {
        //we need to ensure that if this method is called by many different threads at the same time we actually consolidate the requests
        //and send them all at the same time with a single packet as opposed to running a request against the log service for each call.
        //a request for each method call can result in hitting the AWS throttelling limit for the CloudWatch service.
        initializeCloudwatchLogging();
        AtomicInteger logRequestCtr = new AtomicInteger(1);
        eventList.stream()
                .collect(Collectors.groupingBy((t) -> {
                    return t.getModuleId() == -1 ? "ECLIPSE-JEMO" : ("MODULE-" + t.getModuleId() + "-" + String.valueOf(t.getModuleVersion()) + "-" + t.getModuleName()).replaceAll("[^\\.\\-_/#A-Za-z0-9]", "");
                })).entrySet().stream().collect(Collectors.toMap(k -> k.getKey(), v ->
                v.getValue().stream().map(cl -> new InputLogEvent().withMessage(cl.getMessage()).withTimestamp(cl.getTimestamp())).collect(toList())))
                .entrySet().parallelStream().forEach(e -> {
            LOG_SCHEDULER.schedule(() -> {
                try {
                    String logGroup = "ECLIPSE-JEMO";
                    LogStream logStream;
                    boolean hasStream = false;
                    String sequenceToken = null;
                    Iterator<LogStream> logStreamItr = awsLog.describeLogStreams(new DescribeLogStreamsRequest(logGroup).withLogStreamNamePrefix(e.getKey())).getLogStreams().iterator();
                    while (logStreamItr.hasNext()) {
                        logStream = logStreamItr.next();
                        if (logStream.getLogStreamName().equals(e.getKey())) {
                            hasStream = true;
                            sequenceToken = logStream.getUploadSequenceToken();
                            break;
                        }
                    }
                    if (!hasStream) {
                        AbstractJemo.executeFailsafe(awsLog::createLogStream, new CreateLogStreamRequest(logGroup, e.getKey()));
                    }

                    awsLog.putLogEvents(new PutLogEventsRequest(logGroup, e.getKey(), e.getValue())
                            .withSequenceToken(sequenceToken));
                    LOG(Level.FINE, "[%s][%s][%d] Logs sent to the Amazon CloudWatch Service", AmazonAWSRuntime.class.getSimpleName(), e.getKey(), e.getValue().size());
                } catch (Throwable ex) {
                    LOG(Level.WARNING, "[%s][%s] Unable to send logs to the Amazon CloudWatch Service. The error was %s", AmazonAWSRuntime.class.getSimpleName(), e.getKey(), "Type:" + ex.getClass().getName() + " Error: " + ex.getMessage());
                }
            }, logRequestCtr.getAndIncrement() * 350, TimeUnit.MILLISECONDS); //this will run 350 milliseconds after it is posted (this will guarantee fixed rate delivery to CloudWatch)
        });


    }

    @Override
    public void deleteQueue(String queueId) {
        LOG(Level.INFO, "About to delete queue with id: [%s]", queueId);
        getSQS().deleteQueue(queueId);
    }

    /**
     * this method should provide a mechanism for sending messages such
     * that they can then be retrieved by other members of the cluster, naturally this should map
     * to Amazon SQS however this single operation on our lab environment consumes $900 per month
     * in spend.
     * <p>
     * it would be interesting if there is a good alternative to SQS for this operation.
     * especially because SQS does not really deliver the performance we are looking for in the first place.
     * <p>
     * what if we instead of using SQS used dynamodb.
     *
     * @param queueId     the id of the queue to send a message too
     * @param jsonMessage the string body of the message to send.
     * @return the id of the message that was deposited.
     */
    @Override
    public String sendMessage(String queueId, String jsonMessage) {
        //we should check the message size here, because if it is too big then AWS will refuse to recieve it.
        //so if it is large then we should indicate that it is an extended message where the actual content is stored on S3
        //and when processing the message we should retrieve the body from S3 instead.
        //now the next step is to only send big messages in this way because it will be faster to use sqs directly for smaller messages.
        try {
            if (jsonMessage.getBytes("UTF-8").length > 250000) { //this is 1k message size.
                String msgId = UUID.randomUUID().toString();
                store(SQS_DATA_BUCKET, msgId, jsonMessage);
                return getSQS().sendMessage(queueId, msgId).getMessageId();
            } else {
                return getSQS().sendMessage(queueId, jsonMessage).getMessageId();
            }
        } catch (UnsupportedEncodingException encEx) {
            return UUID.randomUUID().toString();
        }
    }

    @Override
    public String getQueueId(String queueName) {
        try {
            return getSQS().getQueueUrl(new GetQueueUrlRequest(queueName)).getQueueUrl();
        } catch (com.amazonaws.services.sqs.model.QueueDoesNotExistException queueDoesNotExist) {
            LOG(Level.FINE, "[%s][%s] the queue does not exist on AWS you need to create the queue before asking for it's id", getClass().getSimpleName(), queueName);
            return null;
        }
    }

    @Override
    public List<String> listQueueIds(String location, boolean includeWorkQueues) {
        return getSQS().listQueues("JEMO-" + (location == null ? "" : location)).getQueueUrls().parallelStream().filter((q) -> {
            return !q.endsWith("-WORK-QUEUE") || includeWorkQueues;
        }).collect(toList());
    }

    @Override
    public int pollQueue(String queueId, CloudQueueProcessor processor) throws QueueDoesNotExistException {
        ReceiveMessageRequest req = createReceiveRequest(queueId);
        final Holder<Integer> messagesProcessed = new Holder<>(0);
        try {
            getSQSAsync().receiveMessageAsync(req, new AsyncHandler<ReceiveMessageRequest, ReceiveMessageResult>() {

                @Override
                public void onError(Exception ex) {
                    //we got an error retrieving a message so print it.
                    LOG(Level.FINE, "[%s][%s] there was an error polling the queue. %s the system will automatically retry shortly.", getClass().getSimpleName(), queueId, ex.getMessage());
                }

                @Override
                public void onSuccess(ReceiveMessageRequest request, ReceiveMessageResult result) {
                    result.getMessages().parallelStream().forEach((msg) -> {
                        messagesProcessed.value++;
                        getSQS().deleteMessage(queueId, msg.getReceiptHandle()); //first we delete so nobody else will get this message.
                        JemoMessage ccMsg = null;
                        int executionCount = 0;
                        try {
                            //the body will actually be a JSON specified message which will contain some standard data
                            String msgId = msg.getBody();
                            if (!msgId.startsWith("{")) {
                                try {
                                    ccMsg = Jemo.fromJSONString(JemoMessage.class, retrieve(SQS_DATA_BUCKET, msgId, String.class));
                                } finally {
                                    delete(SQS_DATA_BUCKET, msgId);
                                }
                            } else {
                                //legacy message processing.
                                ccMsg = Jemo.fromJSONString(JemoMessage.class, msg.getBody());
                            }
                        } catch (IOException jsonEx) {
                        }

                        //the message will contain a relevant plugin-id so we will use that id to know what we need to run.
                        if (ccMsg != null) {
                            JemoMessage fMsg = ccMsg;
                            EVENT_PROCESSOR.submit(() -> {
                                processor.processMessage(fMsg);
                            });
                        }
                    });
                }
            }).get();
        } catch (InterruptedException irrEx) {
        } catch (ExecutionException exEx) {
            throw new QueueDoesNotExistException(exEx.getMessage());
        }

        return messagesProcessed.value;
    }

    private boolean isSystemTable(String tableName) {
        return asList(DYNAMO_SYSTEM_TABLES).contains(tableName.toLowerCase());
    }

    private String getTableRegion(String tableName) {
        if (isSystemTable(tableName)) {
            return getRegionForS3Bucket(S3_PLUGIN_BUCKET());
        }

        return AWSREGION;
    }

    @Override
    public boolean hasNoSQLTable(String tableName) {
        final Holder<Boolean> hasTable = new Holder<>(false);
        getDynamoDB(getTableRegion(tableName)).listTables().forEach((t) -> {
            if (t.getTableName().equalsIgnoreCase(tableName)) {
                hasTable.value = true;
            }
        });

        return hasTable.value;
    }

    @Override
    public void createNoSQLTable(String tableName) {
        Table tbl = getDynamoDB(getTableRegion(tableName)).createTable(tableName, asList(new KeySchemaElement("id", KeyType.HASH)),
                asList(new AttributeDefinition("id", ScalarAttributeType.S)), new ProvisionedThroughput(1L, 1L));
        try {
            tbl.waitForActive();
        } catch (InterruptedException irrEx) {
        }
    }

    @Override
    public <T> List<T> listNoSQL(String tableName, Class<T> objectType) {
        List<T> retval = new ArrayList<>();
        Table dbTable = getDynamoDB(getTableRegion(tableName)).getTable(tableName);
        dbTable.scan().pages().forEach((p) -> p.forEach(item -> {
            try {
                retval.add(Jemo.fromJSONString(objectType, item.getString("data")));
            } catch (IOException ioEx) {
            }
        }));

        return retval;
    }

    @Override
    public <T> List<T> queryNoSQL(String tableName, Class<T> objectType, String... pkList) {
        List<T> retval = new ArrayList<>();
        Table dbTable = getDynamoDB(getTableRegion(tableName)).getTable(tableName);
        AtomicInteger ctr = new AtomicInteger(1);
        Map<String, Object> valueMap = asList(pkList).stream().collect(Collectors.toMap(pk -> ":p" + ctr.getAndIncrement(), pk -> pk));
        String filterQuery = IntStream.range(1, ctr.get()).mapToObj(i -> "id = :p" + String.valueOf(i)).collect(Collectors.joining(" or "));
        dbTable.query(new QuerySpec()
                .withValueMap(valueMap)
                .withKeyConditionExpression(filterQuery)).forEach(i -> {
            try {
                retval.add(Jemo.fromJSONString(objectType, i.getString("data")));
            } catch (IOException ioEx) {
                LOG(Level.WARNING, "could not parse json %s", i.getString("data"));
            }
        });
        return retval;
    }

    @Override
    public <T> T getNoSQL(String tableName, String id, Class<T> objectType) throws IOException {
        Table dbTable = getDynamoDB(getTableRegion(tableName)).getTable(tableName);
        Item dbItem = dbTable.getItem(new KeyAttribute("id", id));
        if (dbItem != null) {
            return Jemo.fromJSONString(objectType, dbItem.getString("data"));
        }

        return null;
    }

    @Override
    public void saveNoSQL(String tableName, SystemDBObject... data) {
        TableWriteItems writeJob = new TableWriteItems(tableName);
        asList(data).stream().forEach(obj -> writeJob.addItemToPut(new Item().withPrimaryKey("id", obj.getId()).withString("data", Jemo._safe_toJSONString(obj))));
        getDynamoDB(getTableRegion(tableName)).batchWriteItem(writeJob);
    }

    @Override
    public void watchdog(final String location, final String instanceId, final String instanceQueueUrl) {
        try {
            final Holder<Boolean> hasTable = new Holder<>(false);
            getDynamoDB(null).listTables().forEach((t) -> {
                if (t.getTableName().equalsIgnoreCase("eclipse_jemo_instances")) {
                    hasTable.value = true;
                }
            });
            if (!hasTable.value) {
                //we need to create the table if it does not already exist.
                Table tbl = getDynamoDB(null).createTable("eclipse_jemo_instances", asList(new KeySchemaElement("instance_id", KeyType.HASH)),
                        asList(new AttributeDefinition("instance_id", ScalarAttributeType.S)), new ProvisionedThroughput(1L, 1L));
                try {
                    tbl.waitForActive();
                } catch (InterruptedException irrEx) {
                }
                LOG(Level.INFO, "DynamoDB instance monitoring table created successfully");
            }
            //we need to save the fact that we are still alive to dynamodb.
            TableWriteItems writeJob = new TableWriteItems("eclipse_jemo_instances");
            writeJob.addItemToPut(new Item().withPrimaryKey("instance_id", instanceId)
                    .withBigInteger("last_seen", BigInteger.valueOf(System.currentTimeMillis()))
                    .withString("instance_location", location)
                    .withString("instance_url", instanceQueueUrl));
            getDynamoDB(null).batchWriteItem(writeJob);
            //we now need to retrieve a list of all of the instances which exist.
            Table configTable = getDynamoDB(null).getTable("eclipse_jemo_instances");
            HashMap<String, Object> paramList = new HashMap<>();
            paramList.put(":ts", (System.currentTimeMillis() - 7200000));
            IteratorSupport<Item, ScanOutcome> rs = configTable.scan("last_seen < :ts", "instance_id,last_seen,instance_location", null, paramList).iterator();
            TableWriteItems deleteJob = new TableWriteItems("eclipse_jemo_instances");
            boolean hasItems = false;
            while (rs.hasNext()) {
                hasItems = true;
                //now anything that appears here is a queue which is here but really should not be and hence should actually be deleted.
                Item instanceItem = rs.next();
                deleteJob.addPrimaryKeyToDelete(new PrimaryKey("instance_id", instanceItem.getString("instance_id")));
                String queueName = "JEMO-" + (instanceItem.getString("instance_location") == null ? location : instanceItem.getString("instance_location")) + "-" + instanceItem.getString("instance_id");
                try {
                    String queueUrl = getSQS().getQueueUrl(queueName).getQueueUrl();
                    getSQS().deleteQueue(queueUrl);
                    LOG(Level.WARNING, "detected dead message queue [%s], Queue has been removed url: [%s]", queueName, queueUrl);
                } catch (Throwable ex) {
                    LOG(Level.WARNING, "detected dead message queue [%s], but it could not be removed because of the error: %s", queueName, ex.getMessage());
                }
            }
            if (hasItems) {
                getDynamoDB(null).batchWriteItem(deleteJob);
            }
        } catch (Throwable ex) {
            LOG(Level.SEVERE, "Unhandled exception in watchdog process %s", JemoError.toString(ex));
        }
    }

    @Override
    public Set<String> listPlugins() {
        HashSet<String> moduleKeyList = new HashSet<>();
        String marker = null;
        ObjectListing objList;
        while ((objList = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).listObjects(new ListObjectsRequest().withBucketName(S3_PLUGIN_BUCKET()).withMaxKeys(500).withMarker(marker))) != null) {
            objList.getObjectSummaries().stream()
                    .filter((m) -> m.getKey().endsWith(".jar"))
                    .forEach((s3obj) -> moduleKeyList.add(s3obj.getKey()));
            if (objList.isTruncated()) {
                marker = objList.getNextMarker();
            } else {
                break;
            }
        }

        return moduleKeyList;
    }

    @Override
    public void uploadModule(String pluginFile, byte[] pluginBytes) {
        uploadModule(pluginFile, new ByteArrayInputStream(pluginBytes), pluginBytes.length);
    }

    @Override
    public void uploadModule(String pluginFile, InputStream in, long moduleSize) {
        ObjectMetadata metaData = new ObjectMetadata();
        metaData.setContentDisposition("filename=\"" + pluginFile + "\"");
        metaData.setContentType("application/jar");

        //make sure we don't have other multipart upload requests running for this key
        List<String> uploadsToAbort = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).listMultipartUploads(new ListMultipartUploadsRequest(S3_PLUGIN_BUCKET())).getMultipartUploads().stream()
                .filter(mp -> mp.getKey().equals(pluginFile))
                .map(mp -> mp.getUploadId())
                .collect(toList());
        for (String uploadId : uploadsToAbort) {
            getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).abortMultipartUpload(new AbortMultipartUploadRequest(S3_PLUGIN_BUCKET(), pluginFile, uploadId));
            LOG(Level.INFO, "Plugin %s aborting failed multipart upload for this file with id %s", new Object[]{pluginFile, uploadId});
        }

        InitiateMultipartUploadRequest initUploadRequest = new InitiateMultipartUploadRequest(S3_PLUGIN_BUCKET(), pluginFile, metaData);
        String uploadId = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).initiateMultipartUpload(initUploadRequest).getUploadId();
        //lets break the upload into 1mb parts
        File uploadDir = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
        uploadDir.mkdirs();
        int numParts = 1;
        final int partLength = 1048576 * 5;
        byte[] buf = new byte[1024];
        int rb = 0;
        try {
            int partSize = 0;
            FileOutputStream partFile = null;
            List<File> partList = new ArrayList<>();
            while ((rb = in.read(buf)) != -1) {
                if (partFile == null) {
                    File partFileItem = new File(uploadDir, "part-" + String.valueOf(numParts));
                    partList.add(partFileItem);
                    partFile = new FileOutputStream(partFileItem);
                }
                partFile.write(buf, 0, rb);
                partSize += rb;
                if (partSize >= partLength) {
                    partFile.close();
                    numParts++;
                    partFile = null;
                    partSize = 0;
                }
            }
            if (partFile != null) {
                partFile.close();
            }
            File[] uploadParts = partList.toArray(new File[]{});
            List<PartETag> partTags = new ArrayList<>();
            for (int i = 0; i < uploadParts.length; i++) {
                UploadPartRequest uploadRequest = new UploadPartRequest();
                uploadRequest.setUploadId(uploadId);
                uploadRequest.setPartNumber(i + 1);
                uploadRequest.setKey(pluginFile);
                uploadRequest.setBucketName(S3_PLUGIN_BUCKET());
                uploadRequest.setPartSize(uploadParts[i].length());
                uploadRequest.setInputStream(new FileInputStream(uploadParts[i]));
                uploadRequest.setObjectMetadata(metaData);
                uploadRequest.setLastPart(i + 1 == uploadParts.length);
                UploadPartResult uploadResponse = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).uploadPart(uploadRequest);
                String etag = uploadResponse.getETag();
                partTags.add(new PartETag(i + 1, etag));
                LOG(Level.INFO, "Plugin Part [%d] %s stored in file %s was uploaded and stored successfully, with ETAG: %s part size was %d", new Object[]{i + 1, pluginFile, uploadParts[i].getName(), etag, uploadParts[i].length()});
            }
            CompleteMultipartUploadRequest completeUploadRequest = new CompleteMultipartUploadRequest(S3_PLUGIN_BUCKET(), pluginFile, uploadId, partTags);
            CompleteMultipartUploadResult completeResponse = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).completeMultipartUpload(completeUploadRequest);
            LOG(Level.INFO, "Plugin %s upload completed. ETAG: %s VERSION %s", new Object[]{pluginFile, completeResponse.getETag(), completeResponse.getVersionId()});
        } catch (IOException ioEx) {
            LOG(Level.WARNING, "[%s] Upload failed as I was unable to read the stream of bytes for the plugin. detailed error was %s", new Object[]{pluginFile, ioEx.getMessage()});
        } catch (AmazonS3Exception s3ex) {
            LOG(Level.WARNING, "[%s] Upload failed. We got the following from Amazon S3 %s. We will abort the upload", new Object[]{pluginFile, s3ex.toString()});
            getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).abortMultipartUpload(new AbortMultipartUploadRequest(S3_PLUGIN_BUCKET(), pluginFile, uploadId));
            throw s3ex;
        } finally {
            Jemo.deleteDirectory(uploadDir);
        }
    }

    private void provisionConfigTable(DynamoDB dynamoDb) {
        final Holder<Boolean> hasTable = new Holder<>(false);
        dynamoDb.listTables().forEach((t) -> {
            if (t.getTableName().equalsIgnoreCase("eclipse_jemo_module_configuration")) {
                hasTable.value = true;
            }
        });
        if (!hasTable.value) {
            //we need to create the table if it does not already exist.
            Table tbl = dynamoDb.createTable("eclipse_jemo_module_configuration", asList(new KeySchemaElement("id", KeyType.HASH), new KeySchemaElement("key", KeyType.RANGE)),
                    asList(new AttributeDefinition("id", ScalarAttributeType.N), new AttributeDefinition("key", ScalarAttributeType.S)), new ProvisionedThroughput(25L, 25L));
            try {
                tbl.waitForActive();
            } catch (InterruptedException irrEx) {
            }
            LOG(Level.INFO, "DynamoDB configuration table created successfully");
        }
    }

    @Override
    public void setModuleConfiguration(int pluginId, ModuleConfiguration config) {
        DynamoDB dynamoDb = getDynamoDB(getTableRegion("eclipse_jemo_module_configuration"));
        provisionConfigTable(dynamoDb);
        if (config != null && !config.getParameters().isEmpty()) {
            TableWriteItems writeJob = new TableWriteItems("eclipse_jemo_module_configuration");
            for (ModuleConfigurationParameter param : config.getParameters()) {
                switch (param.getOperation()) {
                    case delete:
                        writeJob.addPrimaryKeyToDelete(new PrimaryKey("id", pluginId, "key", param.getKey()));
                        break;
                    case upsert:
                        writeJob.addItemToPut(new Item().withPrimaryKey("id", pluginId, "key", param.getKey()).withString("value", param.getValue()));
                        break;
                }
            }
            dynamoDb.batchWriteItem(writeJob);
        }
    }

    @Override
    public Map<String, String> getModuleConfiguration(int pluginId) {
        DynamoDB dynamoDb = getDynamoDB(getTableRegion("eclipse_jemo_module_configuration"));
        provisionConfigTable(dynamoDb); //make sure the table is there before we look for values in it.
        Table configTable = dynamoDb.getTable("eclipse_jemo_module_configuration");
        final Map<String, String> config = new HashMap<>();
        configTable.query("id", pluginId).pages().forEach((p) -> {
            p.forEach((item) -> {
                config.put(item.getString("key"), item.getString("value"));
            });
        });

        return config;
    }

    @Override
    public void store(String key, Object data) {
        store(S3_PLUGIN_BUCKET(), key, data);
    }

    @Override
    public <T> T retrieve(String key, Class<T> objType) {
        return retrieve(S3_PLUGIN_BUCKET(), key, objType);
    }

    private final Set<String> BUCKET_PRESENCE_CACHE = new CopyOnWriteArraySet<>();

    private String buildBucketName(String name) {
        final String suffix = name.endsWith(AWS_ACCOUNT_ID) ? "" : "-" + AWS_ACCOUNT_ID;

        //all bucket names must be relevant to the current account otherwise there will be conflicts
        return name.toLowerCase().replaceAll("[\\_\\.]", "-") + suffix;
    }

    protected void createS3BucketIfNotPresent(String bucketName) {
        if (!BUCKET_PRESENCE_CACHE.contains(bucketName)) {
            try {
                getS3(getRegionForS3Bucket(bucketName)).createBucket(bucketName);
                BUCKET_PRESENCE_CACHE.add(bucketName);
            } catch (AmazonS3Exception s3ex) {
                //this probably means the bucket already exists so just add it to the cache.
                BUCKET_PRESENCE_CACHE.add(bucketName);
            }
        }
    }

    @Override
    public void store(String category, String key, Object data) {
        //so before we store something in this category we need to ensure that the bucket actually exists.
        String bucketName = buildBucketName(category);
        createS3BucketIfNotPresent(bucketName);
        ObjectMetadata metaData = new ObjectMetadata();
        try {
            byte[] byteData = Jemo.toJSONString(data).getBytes("UTF-8");
            metaData.setContentLength(byteData.length);
            metaData.setContentType("application/json");
            metaData.addUserMetadata("implementation", data.getClass().getName());
            getS3(getRegionForS3Bucket(bucketName)).putObject(bucketName, Jemo.md5(key) + ".datastore", new ByteArrayInputStream(byteData), metaData); //post an installation reference to s3.
        } catch (JsonProcessingException | NoSuchAlgorithmException | UnsupportedEncodingException jsonEx) {
            throw new RuntimeException(jsonEx);
        }
    }

    private String getRegionForS3Bucket(final String bucketName) {
        if (!S3_BUCKET_REGION_MAP.containsKey(bucketName)) {
            S3_BUCKET_REGION_MAP.put(bucketName, Optional.ofNullable(AbstractJemo.executeFailsafe(x -> getS3().headBucket(new HeadBucketRequest(bucketName)).getBucketRegion(), null)).orElse(AWSREGION));
        }
        return S3_BUCKET_REGION_MAP.get(bucketName);
    }

    private final Map<String, String> S3_BUCKET_REGION_MAP = new ConcurrentHashMap<>();
    private Pattern bucketParsePattern = Pattern.compile("(The bucket is in this region: )([^\\.]+)");

    @Override
    public <T> T retrieve(String category, String key, Class<T> objType) {
        S3Object obj = null;
        try {
            obj = getS3(getRegionForS3Bucket(buildBucketName(category))).getObject(buildBucketName(category), Jemo.md5(key) + ".datastore");
            String objContent = Jemo.toString(obj.getObjectContent());
            Class objClass = Class.forName(obj.getObjectMetadata().getUserMetaDataOf("implementation"), false, objType.getClassLoader()); //make sure we are using the right class loader for retrieval
            if (objType.isAssignableFrom(objClass)) {
                return (T) (Jemo.fromJSONString(objClass, objContent));
            } else {
                return null;
            }
        } catch (AmazonS3Exception s3Ex) {
            if (s3Ex.getMessage().contains("The bucket is in this region:")) {
                Matcher m = bucketParsePattern.matcher(s3Ex.getMessage());
                if (m.find()) {
                    String bucketRegion = m.group(2);
                    S3_BUCKET_REGION_MAP.put(buildBucketName(category), bucketRegion);
                    LOG(Level.INFO, "[%s][%s] was re-routed to region %s", getClass().getSimpleName(), category, bucketRegion);
                }

                return retrieve(category, key, objType);
            } else if (!s3Ex.getMessage().contains("Status Code: 404;")) {
                throw s3Ex;
            }

            return null;
        } catch (NoSuchAlgorithmException | ClassNotFoundException | IOException jsonEx) {
            throw new RuntimeException(jsonEx);
        } catch (AmazonClientException awsEx) {
            if (!awsEx.getMessage().contains("Status Code: 404;")) {
                throw awsEx;
            }

            return null;
        } finally {
            if (obj != null) {
                try {
                    obj.close();
                } catch (IOException ioex) {
                }
            }
        }
    }

    @Override
    public void delete(String category, String key) {
        try {
            getS3(getRegionForS3Bucket(buildBucketName(category))).deleteObject(buildBucketName(category), Jemo.md5(key) + ".datastore");
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException(ex);
        }
    }

    @Override
    public void write(String category, String path, String key, InputStream dataStream) {
        //we will want to create a backed temporary file for this because we don't know what the stream length is.
        try {
            File tmpFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
            try {
                Jemo.stream(new FileOutputStream(tmpFile), dataStream, false);
                ObjectMetadata metaData = new ObjectMetadata();
                metaData.setContentLength(tmpFile.length());
                String bucketName = buildBucketName(category);
                createS3BucketIfNotPresent(bucketName);
                getS3(getRegionForS3Bucket(bucketName)).putObject(bucketName, path + "/" + key, new FileInputStream(tmpFile), metaData);
            } finally {
                tmpFile.delete();
            }
        } catch (IOException ioex) {
            throw new RuntimeException(ioex);
        }
    }

    @Override
    public InputStream read(String category, String path, String key) {
        try {
            //we need to implement this such that it will temporarily spool to a file and then from that file return a stream reference
            return new S3ObjectInputStream(getS3(getRegionForS3Bucket(buildBucketName(category))).getObject(buildBucketName(category), path + "/" + key));
        } catch (AmazonS3Exception amazonEx) {
            return null;
        }
    }

    @Override
    public void remove(String category, String path, String key) {
        final String bucketName = buildBucketName(category);
        final AmazonS3 s3 = getS3(getRegionForS3Bucket(bucketName));
        if (s3.doesBucketExistV2(bucketName) && s3.doesObjectExist(bucketName, path == null ? key : path + "/" + key)) {
            s3.deleteObject(new DeleteObjectRequest(bucketName, path == null ? key : path + "/" + key));
        }
    }

    @Override
    public ValidationResult validatePermissions() {
        // If the user credentials are not validated, validated them first.
        if (arn == null) {
            ValidationResult validationResult = validateCredentials(AWS_CREDENTIALS());
            if (!validationResult.isSuccess()) {
                return validationResult;
            }
        }

        SimulatePrincipalPolicyResult result;
        try {
            result = getIAM().simulatePrincipalPolicy(new SimulatePrincipalPolicyRequest()
                    .withActionNames(ACTION_NAMES)
                    .withPolicySourceArn(arn));
        } catch (Throwable e) {
            LOG(Level.WARNING, String.format("User permissions validation failed: [%s]. Trying validating the role instead.", e.getMessage()));
            return new ValidationResult(Collections.singletonList("Error: " + e.getMessage()));
        }
        final List<String> notPermittedActions = result.getEvaluationResults().stream()
                .filter(evaluationResult -> !ALLOWED.equals(evaluationResult.getEvalDecision()))
                .map(EvaluationResult::getEvalActionName)
                .collect(toList());
        ValidationResult validationResult = new ValidationResult(notPermittedActions);
        if (!validationResult.isSuccess()) {
            LOG(Level.WARNING, String.format("The following user permissions are missing: [%s]", notPermittedActions));
        }
        return validationResult;
    }

    @Override
    public ValidationResult validateCredentials(Map<String, String> credentials) {
        final String accessKey = credentials.get(AWS_ACCESS_KEY_ID);
        final String secretKey = credentials.get(AWS_SECRET_ACCESS_KEY);
        final AWSCredentialsProvider credentialsProvider = new AWSStaticCredentialsProvider(new BasicAWSCredentials(accessKey, secretKey));
        return validateCredentials(credentialsProvider);
    }

    private ValidationResult validateCredentials(AWSCredentialsProvider credentialsProvider) {
        try {
            final AmazonIdentityManagement iam_client = buildClient(AmazonIdentityManagement.class, AmazonIdentityManagementClientBuilder.standard(), AWSREGION, credentialsProvider);
            arn = iam_client.getUser().getUser().getArn();
            // Is there a way to decide if I need to get the user or the role arn?
            this.iam_client = iam_client;
            return ValidationResult.SUCCESS;
        } catch (Throwable t) {
            LOG(Level.FINE, String.format("User credentials validation failed: [%s]. Looking to find a role instead", t.getMessage()));
            return findRoleArn(credentialsProvider);
        }
    }

    @Override
    public ValidationResult validatePolicy(String policyName) {
        final String policyArn = "arn:aws:iam::" + AWS_ACCOUNT_ID + ":policy/" + policyName;
        final String defaultVersion = getIAM().listPolicyVersions(new ListPolicyVersionsRequest().withPolicyArn(policyArn)).getVersions().stream()
                .filter(PolicyVersion::isDefaultVersion)
                .map(PolicyVersion::getVersionId)
                .findFirst().get();

        final String policyDocument = F(null, (x -> URLDecoder.decode(
                getIAM().getPolicyVersion(new GetPolicyVersionRequest().withPolicyArn(policyArn).withVersionId(defaultVersion)).getPolicyVersion().getDocument(),
                Charset.defaultCharset().toString())));
        final SimulateCustomPolicyResult result = getIAM().simulateCustomPolicy(new SimulateCustomPolicyRequest()
                .withActionNames(ACTION_NAMES)
                .withPolicyInputList(policyDocument));
        final List<String> notPermittedActions = result.getEvaluationResults().stream()
                .filter(evaluationResult -> !ALLOWED.equals(evaluationResult.getEvalDecision()))
                .map(EvaluationResult::getEvalActionName)
                .collect(toList());
        ValidationResult validationResult = new ValidationResult(notPermittedActions);
        if (!validationResult.isSuccess()) {
            LOG(Level.WARNING, String.format("The following user permissions are missing: [%s]", notPermittedActions));
        }
        return validationResult;
    }

    private ValidationResult findRoleArn(AWSCredentialsProvider credentialsProvider) {
        try {
            final AmazonIdentityManagement iam_client = buildClient(AmazonIdentityManagement.class, AmazonIdentityManagementClientBuilder.standard(), AWSREGION, credentialsProvider);

            final AWSSecurityTokenService sts_client = buildClient(AWSSecurityTokenService.class, AWSSecurityTokenServiceClientBuilder.standard(), AWSREGION, credentialsProvider);
            GetCallerIdentityResult callerIdentity = sts_client.getCallerIdentity(new GetCallerIdentityRequest());
            LOG(Level.INFO, String.format("Caller identity, Account=%s, UserId=%s, Arn=%s", callerIdentity.getAccount(),
                    callerIdentity.getUserId(), callerIdentity.getArn()));

            final String str = ":assumed-role/";
            final int index = callerIdentity.getArn().indexOf(str);
            final int beginIndex = index + str.length();
            final int endIndex = callerIdentity.getArn().indexOf("/", beginIndex);
            final String roleName = callerIdentity.getArn().substring(beginIndex, endIndex);
            LOG(Level.FINE, String.format("Found role attached to the instance: [%s]", roleName));

            arn = iam_client.getRole(new GetRoleRequest().withRoleName(roleName)).getRole().getArn();
            this.iam_client = iam_client;
            LOG(Level.FINE, String.format("Found role with arn: [%s]", arn));
        } catch (Throwable t) {
            LOG(Level.FINE, String.format("Failed to retrieve account Id from instance: [%s]", t.getMessage()));
            return new ValidationResult(Collections.singletonList(t.getMessage()));
        }
        return ValidationResult.SUCCESS;
    }

    @Override
    public void setRegion(String regionCode) throws IOException {
        AWSREGION = regionCode;
        final Path awsDirectory = awsDirectory();
        if (!Files.exists(awsDirectory)) {
            Files.createDirectories(awsDirectory);
        }
        final Path configFilePath = awsDirectory.resolve("config");
        final String content = "[default]\n" +
                "region = " + regionCode + "\n";
        Files.deleteIfExists(configFilePath);
        Files.write(configFilePath, content.getBytes(), CREATE);
    }

    @Override
    public String readInstanceTag(String key) {
        final String instanceId = EC2MetadataUtils.getInstanceId();
        LOG(Level.INFO, String.format("Searching for tag [%s] on instance [%s]", key, instanceId));

        final DescribeTagsResult describeTagsResult = getEC2().describeTags(new DescribeTagsRequest()
                .withFilters(
                        new Filter().withName("key").withValues(key),
                        new Filter().withName("resource-type").withValues("instance"),
                        new Filter().withName("resource-id").withValues(instanceId)
                )
        );

        return describeTagsResult.getTags().isEmpty() ? null : describeTagsResult.getTags().get(0).getValue();
    }

    @Override
    public List<RegionInfo> getRegions() {
        return asList(new RegionInfo("us-east-1", "US East (N. Virginia)"),
                new RegionInfo("us-east-2", "US East (Ohio)"),
                new RegionInfo("us-west-1", "US West (N. California)"),
                new RegionInfo("us-west-2", "US West (Oregon)"),
                new RegionInfo("ap-south-1", "Asia Pacific (Mumbai)"),
                new RegionInfo("ap-northeast-1", "Asia Pacific (Tokyo)"),
                new RegionInfo("ap-northeast-2", "Asia Pacific (Seoul)"),
                new RegionInfo("ap-northeast-3", "Asia Pacific (Osaka-Local)"),
                new RegionInfo("ap-southeast-1", "Asia Pacific (Singapore)"),
                new RegionInfo("ap-southeast-2", "Asia Pacific (Sydney)"),
                new RegionInfo("ca-central-1", "Canada (Central)"),
                new RegionInfo("cn-north-1", "China (Beijing)"),
                new RegionInfo("cn-northwest-1", "China (Ningxia)"),
                new RegionInfo("eu-central-1", "EU (Frankfurt)"),
                new RegionInfo("eu-west-1", "EU (Ireland)"),
                new RegionInfo("eu-west-2", "EU (London)"),
                new RegionInfo("eu-west-3", "EU (Paris)"),
                new RegionInfo("eu-north-1", "EU (Stockholm)"),
                new RegionInfo("sa-east-1", "South America (São Paulo)"));
    }

    @Override
    public String getQueueName(String queueId) {
        return queueId.substring(queueId.lastIndexOf('/') + 1);
    }

    @Override
    public void resetLogConsoleHandler(Handler handler) {
        LOG.removeHandler(LOG.getHandlers()[0]);
        LOG.addHandler(handler);
        LOG.setLevel(handler.getLevel());
    }

    private String generateAuthToken(String clusterName) {
        // For more info read: https://telegraphhillsoftware.com/awseksk8sclientauth/

        Request<Void> request = new DefaultRequest<>("sts");
        request.setHttpMethod(HttpMethodName.GET);
        request.setEndpoint(URI.create("https://sts.amazonaws.com/"));

        request.addParameter("Action", "GetCallerIdentity");
        request.addParameter("Version", "2011-06-15");
        request.addHeader("x-k8s-aws-id", clusterName);
        AWS4Signer signer = new AWS4Signer();
        signer.setRegionName("us-east-1"); // needs to be us-east-1
        signer.setServiceName("sts");

        signer.presignRequest(request, AWS_CREDENTIALS().getCredentials(),
                new Date(System.currentTimeMillis() + TimeUnit.SECONDS.toMillis(60))); // must be <=60 seconds

        StringBuilder sb = new StringBuilder();

        sb.append("https://sts.amazonaws.com/");

        AtomicInteger count = new AtomicInteger(0);
        request.getParameters().forEach((k, v) ->
                {
                    try {
                        sb.append(count.getAndIncrement() == 0 ? "?" : "&")
                                .append(URLEncoder.encode(k, Util.UTF8_CHARSET.name()))
                                .append("=")
                                .append(URLEncoder.encode(v.get(0), Util.UTF8_CHARSET.name()));
                    } catch (UnsupportedEncodingException e) {
                        throw new RuntimeException(e);
                    }
                }
        );

        return "k8s-aws-v1." + BaseEncoding.base64Url().encode(sb.toString().getBytes());
    }

    @Override
    public List<String> getExistingNetworks() {
        return getEC2().describeVpcs().getVpcs().stream()
                .map(vpc -> vpc.getTags().stream()
                        .filter(tag -> tag.getKey().equals("Name"))
                        .findFirst()
                        .map(tag -> tag.getValue() + " | " + vpc.getVpcId()).orElse(null))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    @Override
    public List<String> getCustomerManagedPolicies() {
        return getIAM().listPolicies(new ListPoliciesRequest().withScope(PolicyScopeType.Local)).getPolicies().stream()
                .map(policy -> policy.getPolicyName())
                .collect(toList());
    }

    @Override
    public JemoRuntimeSetup.TerraformJobResponse install(String region, StringBuilder builder) throws IOException {
        final Path terraformDirPath = createInstallTerraformTemplates(region);
        final TerraformJob terraformJob = new TerraformJob(terraformDirPath.toString(), terraformDirPath.toString() + "/" + JemoRuntimeSetup.TFVARS_FILE_NAME).run(builder);

        // There is a delay between the time that terraform finishes and the user credentials are valid to be used by AWS services.
        Util.B(null, x -> TimeUnit.SECONDS.sleep(10));
        Files.copy(Paths.get("terraform.tfstate"), terraformDirPath.resolve("terraform.tfstate"));
        return JemoRuntimeSetup.TerraformJobResponse.fromTerraformJob(terraformJob);
    }

    @Override
    public Map<String, String> getCredentialsFromTerraformResult(TerraformResult terraformResult) {
        return new HashMap<String, String>() {{
            put(AWS_ACCESS_KEY_ID, terraformResult.getOutput("jemo_user_access_key_id"));
            put(AWS_SECRET_ACCESS_KEY, terraformResult.getOutput("jemo_user_secret_access_key"));
        }};
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
            final String tfvarsFileContent = "terraform_user_access_key=\"" + AWS_CREDENTIALS().getCredentials().getAWSAccessKeyId() + "\"\n" +
                    "terraform_user_secret_key=\"" + AWS_CREDENTIALS().getCredentials().getAWSSecretKey() + "\"\n" +
                    "region=\"" + region + "\"\n";
            Files.write(Paths.get(terraformDirPath.toString() + "/" + JemoRuntimeSetup.TFVARS_FILE_NAME), tfvarsFileContent.getBytes(), CREATE);
        }
        return terraformDirPath;
    }

    @Override
    public ClusterParams getClusterParameters() {
        return new ClusterParams(
                asList(
                        new ClusterParam("cluster-name", "jemo-cluster", "the cluster name"),
                        new ClusterParam("cluster-role-name", "jemo-cluster-role", "the cluster role name"),
                        new ClusterParam("cluster-security-group-name", "jemo-cluster-security-group", "the cluster security group name"),
                        new ClusterParam("cluster-security-group-name-tag", "jemo-cluster", "the cluster security group name tag"),
                        new ClusterParam("workstation-external-cidr", "", "comma separated list of local workstation IPs allowed to access the cluster")
                ),
                asList(
                        new ClusterParam("worker-node-role-name", "jemo-node-role", "the worker nodes role name"),
                        new ClusterParam("worker-node-instance-profile-name", "jemo-node-instance-profile", "the worker nodes instance profile name"),
                        new ClusterParam("worker-node-security-group-name", "jemo-node-security-group", "the worker nodes security group name"),
                        new ClusterParam("launch-conf-instance-type", "t2.micro", "the AWS launch configuration instance type",
                                asList(
                                        "a1.medium", "a1.large", "a1.xlarge", "a1.2xlarge", "a1.4xlarge",
                                        "t3.nano", "t3.micro", "t3.small", "t3.medium", "t3.large", "t3.xlarge", "t3.2xlarge",
                                        "t2.nano", "t2.micro", "t2.small", "t2.medium", "t2.large", "t2.xlarge", "t2.2xlarge",
                                        "m5.large", "m5.xlarge", "m5.2xlarge", "m5.4xlarge", "m5.12xlarge", "m5.24xlarge", "m5d.large", "m5d.xlarge", "m5d.2xlarge", "m5d.4xlarge", "m5d.12xlarge", "m5d.24xlarge",
                                        "m5a.large", "m5a.xlarge", "m5a.2xlarge", "m5a.4xlarge", "m5a.12xlarge", "m5a.24xlarge",
                                        "m4.large", "m4.xlarge", "m4.2xlarge", "m4.4xlarge", "m4.10xlarge", "m4.16xlarge"
                                )),
                        new ClusterParam("launch-conf-name-prefix", "jemo", "the AWS launch configuration name prefix"),
                        new ClusterParam("autoscaling-group-name", "jemo", "the AWS autoscaling group name"),
                        new ClusterParam("autoscaling-group-desired-capacity", "2", "the AWS autoscaling group capacity"),
                        new ClusterParam("autoscaling-group-max-size", "2", "the AWS autoscaling group max size"),
                        new ClusterParam("autoscaling-group-min-size", "1", "the AWS autoscaling group min size")
                ),
                asList(
                        new ClusterParam("vpc-name-tag", "jemo-vpc", "the cluster VPC name tag"),
                        new ClusterParam("subnet-name-tag", "jemo-subnet", "the subnet name tag"),
                        new ClusterParam("internet-gateway-name-tag", "jemo-internet-gateway", "the internet gateway name tag")
                )
        );
    }

    @Override
    public JemoRuntimeSetup.ClusterCreationResponse createCluster(JemoRuntimeSetup.SetupParams setupParams, StringBuilder builder) throws IOException {
        prepareClusterCreation(setupParams);

        setupParams.parameters().put("terraform_user_access_key", AWS_CREDENTIALS().getCredentials().getAWSAccessKeyId());
        setupParams.parameters().put("terraform_user_secret_key", AWS_CREDENTIALS().getCredentials().getAWSSecretKey());
        final Path terraformDirPath = prepareTerraformFiles(setupParams);

        final TerraformJob terraformJob = new TerraformJob(terraformDirPath.toString(), terraformDirPath.toString() + "/" + JemoRuntimeSetup.TFVARS_FILE_NAME).run(builder);
        final Path source = Paths.get("terraform.tfstate");
        if (Files.exists(source)) {
            Files.copy(source, terraformDirPath.resolve("terraform.tfstate"));
        }
        if (!terraformJob.isSuccessful()) {
            return JemoRuntimeSetup.ClusterCreationResponse.fromTerraformJob(terraformJob);
        }

        TerraformResult terraformResult = terraformJob.getResult();
        final String clusterUrl = terraformResult.getOutput("aws_eks_cluster_endpoint");
        final String jemoWorkerNodeRole = terraformResult.getOutput("aws_eks_worker_node_role_arn");
        final String token = generateAuthToken(setupParams.parameters().get("cluster-name"));
        final Integer replicas = Integer.valueOf(setupParams.parameters().get("autoscaling-group-desired-capacity"));
        final String loadBalancerUrl = createKubernetesPodsAndService(clusterUrl, token, replicas, jemoWorkerNodeRole, builder);
        reportAndLog(builder, "Jemo will be accessible after 1-2 minutes on: " + loadBalancerUrl);
        return JemoRuntimeSetup.ClusterCreationResponse.fromTerraformJob(terraformJob).setLoadBalancerUrl(loadBalancerUrl);
    }

    @Override
    public Path createClusterTerraformTemplates(JemoRuntimeSetup.SetupParams setupParams) throws IOException {
        prepareClusterCreation(setupParams);
        final Path terraformDirPath = prepareTerraformFiles(setupParams);

        final Path kubernetesDirPath = terraformDirPath.resolve("kubernetes");
        if (!Files.exists(kubernetesDirPath)) {
            Files.createDirectory(kubernetesDirPath);
        }
        final String sourceDir = getTerraformClusterDir() + "kubernetes/";
        copy(sourceDir, kubernetesDirPath, "jemo-svc.yaml", getClass());
        final String replicas = setupParams.parameters().get("autoscaling-group-desired-capacity");
        applyTemplate(sourceDir, kubernetesDirPath, "jemo-statefulset.yaml", getClass(), x -> x.replaceAll("_JEMO_REPLICAS_", replicas).replaceAll("_REGION_", AWSREGION));
        return terraformDirPath;
    }

    @Override
    public List<InstallProperty> getInstallProperties() {
        return null;
    }

    @Override
    public void setInstallProperties(Map<String, String> properties) {
        // Do nothing.
    }

    @Override
    public AdminUserCreationInstructions getAdminUserCreationInstructions() {
        return new AdminUserCreationInstructions("Jemo setup requires an admin AWS user to run terraform with.",
                asList("Create a user with \"Programmatic access\" and attach the \"AdministratorAccess\" policy."));
    }

    @Override
    public void deleteKubernetesResources(StringBuilder builder) throws IOException {
        runProcess(builder, new String[]{
                "/bin/sh", "-c", "aws eks update-kubeconfig --name jemo-cluster ; " +
                "kubectl delete statefulset jemo ; " +
                "kubectl delete svc jemo ; " +
                "kubectl delete -n kube-system configmap aws-auth"
        });
    }

    @Override
    public String getCspLabel() {
        return "aws";
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
        final boolean isInstalled = isCommandInstalled("aws --help");
        return isInstalled ? null : "Please install 'aws'. Instructions on https://docs.aws.amazon.com/cli/latest/userguide/cli-chap-install.html";
    }

    private Path prepareTerraformFiles(JemoRuntimeSetup.SetupParams setupParams) throws IOException {
        final String terraformDir = getTerraformClusterDir();
        final Path terraformDirPath = Paths.get(terraformDir);
        if (Files.exists(terraformDirPath)) {
            Util.deleteDirectory(terraformDirPath.toFile());
        }
        Files.createDirectories(terraformDirPath);

        final String existingVpcName = setupParams.parameters().get("existing-network-name");

        final String jemoVpcIdValue = existingVpcName == null ? "aws_vpc.jemo.id" : "var.existing-vpc-id";
        final String jemoSubnetIds = existingVpcName == null ? "aws_subnet.jemo.*.id" : "data.aws_subnet_ids.jemo.ids";
        final Function<String, String> replaceFunc = x -> x.replaceAll("_JEMO_VPC_ID_", jemoVpcIdValue)
                .replaceAll("_JEMO_SUBNET_IDS_", jemoSubnetIds);
        copy(terraformDir, terraformDirPath, "README.txt", getClass());
        copy(terraformDir, terraformDirPath, "outputs.tf", getClass());
        copy(terraformDir, terraformDirPath, "variables.tf", getClass());
        applyTemplate(terraformDir, terraformDirPath, "eks-cluster.tf", getClass(), replaceFunc);
        applyTemplate(terraformDir, terraformDirPath, "eks-worker-nodes.tf", getClass(), replaceFunc);

        if (existingVpcName == null) {
            copy(terraformDir, terraformDirPath, "vpc.tf", getClass());
            copy(terraformDir, terraformDirPath, "providers.tf", getClass());
        } else {
            applyTemplate(terraformDir, terraformDirPath, "providers.tf", getClass(), x -> x +
                    "\ndata \"aws_subnet_ids\" \"jemo\" {\n" +
                    "  vpc_id = \"${var.existing-vpc-id}\"\n" +
                    "}");
            final String vpcId = existingVpcName.split("\\| ")[1];
            setupParams.parameters().put("existing-vpc-id", vpcId);
        }

        setupParams.parameters().put("region", AWSREGION);
        final String varsFileContent = setupParams.parameters().keySet().stream()
                .filter(key -> !key.equals("existing-network-name") && !key.equals("containersPerParamSet"))
                .map(key -> key + "=\"" + setupParams.parameters().get(key) + "\"")
                .collect(Collectors.joining("\n"));

        Files.write(Paths.get(terraformDirPath.toString() + "/" + JemoRuntimeSetup.TFVARS_FILE_NAME), varsFileContent.getBytes(), CREATE);
        return terraformDirPath;
    }

    private String createKubernetesPodsAndService(String clusterUrl, String token, Integer replicas, String jemoWorkerNodeRole, StringBuilder builder) {
        String loadBalancerUrl;

        try {
            final ApiClient apiClient = Config.fromToken(clusterUrl, token, false);
            Configuration.setDefaultApiClient(apiClient);
            final AppsV1Api appsV1Api = new AppsV1Api();
            final CoreV1Api coreV1Api = new CoreV1Api();

            reportAndLog(builder, "Creating the config map...");
            V1ConfigMap configMap = new V1ConfigMap()
                    .apiVersion("v1")
                    .kind("ConfigMap")
                    .metadata(new V1ObjectMeta()
                            .name("aws-auth")
                            .namespace("kube-system"))
                    .putDataItem("mapRoles",
                            "- rolearn: " + jemoWorkerNodeRole + "\n" +
                                    "  username: system:node:{{EC2PrivateDNSName}}\n" +
                                    "  groups:\n" +
                                    "    - system:bootstrappers\n" +
                                    "    - system:nodes\n");
            coreV1Api.createNamespacedConfigMap("kube-system", configMap, null, null, null);

            reportAndLog(builder, "Creating the jemo pods...");
            final V1StatefulSet v1StatefulSet = new V1StatefulSet()
                    .apiVersion("apps/v1")
                    .kind("StatefulSet")
                    .metadata(new V1ObjectMeta().name("jemo"))
                    .spec(new V1StatefulSetSpec()
                            .replicas(replicas)
                            .selector(new V1LabelSelector()
                                    .putMatchLabelsItem("app", "jemo"))
                            .template(new V1PodTemplateSpec()
                                    .metadata(new V1ObjectMeta().putLabelsItem("app", "jemo"))
                                    .spec(new V1PodSpec()
                                            .containers(asList(
                                                    new V1Container()
                                                            .name("jemo")
                                                            .image("eclipse/jemo:1.0.8-SNAPSHOT")
                                                            .env(asList(
                                                                    new V1EnvVar().name(AWS_REGION_PROP).value(AWSREGION),
                                                                    new V1EnvVar().name(CLOUD.label()).value("AWS"),
                                                                    new V1EnvVar().name(HTTP_PORT.label()).value("80"),
                                                                    new V1EnvVar().name(HTTPS_PORT.label()).value("443")))
                                                            .ports(singletonList(new V1ContainerPort().containerPort(80)))
                                            )))));
            appsV1Api.createNamespacedStatefulSet("default", v1StatefulSet, null, null, null);

            long start = System.currentTimeMillis();
            long duration;
            V1StatefulSet createdStatefulSet;
            do {
                createdStatefulSet = appsV1Api.readNamespacedStatefulSet("jemo", "default", "true", null, null);
                duration = (System.currentTimeMillis() - start) / 60_000;
            } while (duration < 5 && !replicas.equals(createdStatefulSet.getStatus().getReadyReplicas()));

            reportAndLog(builder, "Creating the jemo load balancer service...");
            final V1Service jemoLoadBalancerService = new V1Service()
                    .apiVersion("v1")
                    .kind("Service")
                    .metadata(new V1ObjectMeta()
                            .name("jemo")
                            .putLabelsItem("app", "jemo")
                            .putAnnotationsItem("service.beta.kubernetes.io/aws-load-balancer-type", "nlb"))
                    .spec(new V1ServiceSpec()
                            .ports(asList(
                                    new V1ServicePort().name("http").port(80).protocol("TCP"),
                                    new V1ServicePort().name("https").port(443).protocol("TCP")
                            ))
                            .putSelectorItem("app", "jemo")
                            .type("LoadBalancer"));
            coreV1Api.createNamespacedService("default", jemoLoadBalancerService, null, null, null);
            loadBalancerUrl = getLoadBalancerUrl(coreV1Api);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return loadBalancerUrl;
    }

    private String getLoadBalancerUrl(CoreV1Api coreV1Api) throws ApiException {
        long start = System.currentTimeMillis();
        long duration;
        V1Service createdService;
        do {
            createdService = coreV1Api.readNamespacedService("jemo", "default", "true", null, null);
            duration = (System.currentTimeMillis() - start) / 60_000;
        } while (duration < 3 && isLoadBalancerUrlCreated(createdService));

        final V1LoadBalancerIngress loadBalancerIngress = createdService.getStatus().getLoadBalancer().getIngress().get(0);
        return "http://" + (loadBalancerIngress.getIp() == null ? loadBalancerIngress.getHostname() : loadBalancerIngress.getIp());
    }

    private boolean isLoadBalancerUrlCreated(V1Service createdService) {
        final List<V1LoadBalancerIngress> ingresses = createdService.getStatus().getLoadBalancer().getIngress();
        return ingresses == null || (ingresses.get(0).getHostname() == null && ingresses.get(0).getIp() == null);
    }

    private void reportAndLog(StringBuilder builder, String message) {
        builder.append('\n').append(message).append('\n');
        LOG(Level.INFO, message);
    }

}
