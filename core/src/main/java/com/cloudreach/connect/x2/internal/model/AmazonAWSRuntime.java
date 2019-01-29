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
package com.cloudreach.connect.x2.internal.model;

import com.amazonaws.AmazonClientException;
import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.SystemPropertiesCredentialsProvider;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.handlers.AsyncHandler;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;
import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.KeyAttribute;
import com.amazonaws.services.dynamodbv2.document.PrimaryKey;
import com.amazonaws.services.dynamodbv2.document.ScanOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;
import com.amazonaws.services.dynamodbv2.document.TableWriteItems;
import com.amazonaws.services.dynamodbv2.document.internal.IteratorSupport;
import com.amazonaws.services.dynamodbv2.document.spec.QuerySpec;
import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.DeleteTableRequest;
import com.amazonaws.services.dynamodbv2.model.DeleteTableResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;
import com.amazonaws.services.dynamodbv2.util.TableUtils;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagement;
import com.amazonaws.services.identitymanagement.AmazonIdentityManagementClientBuilder;
import com.amazonaws.services.logs.AWSLogsAsync;
import com.amazonaws.services.logs.AWSLogsAsyncClientBuilder;
import com.amazonaws.services.logs.model.AWSLogsException;
import com.amazonaws.services.logs.model.CreateLogGroupRequest;
import com.amazonaws.services.logs.model.CreateLogStreamRequest;
import com.amazonaws.services.logs.model.DescribeLogStreamsRequest;
import com.amazonaws.services.logs.model.InputLogEvent;
import com.amazonaws.services.logs.model.LogStream;
import com.amazonaws.services.logs.model.PutLogEventsRequest;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.AbortMultipartUploadRequest;
import com.amazonaws.services.s3.model.AmazonS3Exception;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.CompleteMultipartUploadRequest;
import com.amazonaws.services.s3.model.CompleteMultipartUploadResult;
import com.amazonaws.services.s3.model.CreateBucketRequest;
import com.amazonaws.services.s3.model.DeleteObjectRequest;
import com.amazonaws.services.s3.model.GetObjectRequest;
import com.amazonaws.services.s3.model.HeadBucketRequest;
import com.amazonaws.services.s3.model.InitiateMultipartUploadRequest;
import com.amazonaws.services.s3.model.ListBucketsRequest;
import com.amazonaws.services.s3.model.ListMultipartUploadsRequest;
import com.amazonaws.services.s3.model.ListObjectsRequest;
import com.amazonaws.services.s3.model.ListObjectsV2Request;
import com.amazonaws.services.s3.model.ListObjectsV2Result;
import com.amazonaws.services.s3.model.ObjectListing;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.amazonaws.services.s3.model.S3Object;
import com.amazonaws.services.s3.model.S3ObjectSummary;
import com.amazonaws.services.s3.model.UploadPartRequest;
import com.amazonaws.services.s3.model.UploadPartResult;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSAsync;
import com.amazonaws.services.sqs.AmazonSQSAsyncClientBuilder;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.CreateQueueRequest;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.GetQueueUrlRequest;
import com.amazonaws.services.sqs.model.QueueAttributeName;
import com.amazonaws.services.sqs.model.ReceiveMessageRequest;
import com.amazonaws.services.sqs.model.ReceiveMessageResult;
import com.amazonaws.services.sqs.model.SetQueueAttributesRequest;
import com.cloudreach.connect.x2.CC;
import static com.cloudreach.connect.x2.CC.executeFailsafe;
import com.cloudreach.connect.x2.sys.CCPluginManager;
import com.cloudreach.connect.x2.sys.internal.CCNullOutputStream;
import com.cloudreach.connect.x2.sys.internal.SystemDB;
import com.cloudreach.connect.x2.sys.internal.Util;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.xml.ws.Holder;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class AmazonAWSRuntime implements CloudRuntime {
	
	private static AWSCredentialsProvider AWS_CREDENTIALS_PROVIDER = null;
	private static final ExecutorService EVENT_PROCESSOR = Executors.newCachedThreadPool();
	private static final String[] DYNAMO_SYSTEM_TABLES = new String[] {"cloudreach_x2_module_configuration","cloudreach_x2_security_groups","cloudreach_x2_security_users", "cloudreach_x2_modules"};
	public static final Region AWSREGION = Region.getRegion(Regions.fromName(System.getProperty("cloudreach.x2.aws.region",Regions.EU_WEST_1.getName())));
	
	public static final AWSCredentialsProvider AWS_CREDENTIALS() {
		if(AWS_CREDENTIALS_PROVIDER == null) {
			if(System.getProperty("aws.accessKeyId") != null && System.getProperty("aws.secretKey") != null) {
				AWS_CREDENTIALS_PROVIDER = new SystemPropertiesCredentialsProvider();
			} else {
				AWS_CREDENTIALS_PROVIDER = new EC2ContainerCredentialsProviderWrapper();
			}
		}
		
		return AWS_CREDENTIALS_PROVIDER;
	}
	
	private static DynamoDB _sys_dynamoDb = null;

	@Override
	public void deleteNoSQL(String tableName, SystemDBObject... data) {
		TableWriteItems writeJob = new TableWriteItems(tableName);
		Arrays.asList(data).stream().forEach(obj -> writeJob.addPrimaryKeyToDelete(new PrimaryKey("id", obj.getId())));
		getDynamoDB(getTableRegion(tableName)).batchWriteItem(writeJob);
	}

	@Override
	public void dropNoSQLTable(String tableName) {
		DeleteTableResult deleteTable = getDynamoDB(getTableRegion(tableName)).getTable(tableName).delete();
		//we should wait for the table to disappear before returning.
		while(hasNoSQLTable(tableName)) {
			try { Thread.sleep(100); }catch(InterruptedException irrEx) {}
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
		if(s3.doesBucketExistV2(bucketName)) {
			return StreamSupport.stream(Spliterators.spliteratorUnknownSize(new Iterator<InputStream>() {

				ListObjectsV2Result result = null;
				InputStream currentStream = null;
				Iterator<S3ObjectSummary> currentSummaryList = null;

				@Override
				public synchronized boolean hasNext() {
					if(currentStream == null) {
						currentStream = nextStream();
					}

					return currentStream != null;
				}

				@Override
				public synchronized InputStream next() {
					if(currentStream == null) {
						currentStream = nextStream();
					}
					try {
						InputStream retval = currentStream;
						return retval;
					}finally {
						currentStream = null;
					}
				}

				private synchronized InputStream nextStream() {
					if(currentSummaryList != null && currentSummaryList.hasNext()) {
						S3ObjectSummary objSummary = currentSummaryList.next();
						return s3.getObject(new GetObjectRequest(objSummary.getBucketName(), objSummary.getKey())).getObjectContent();
					} else if(result == null) {
						result = s3.listObjectsV2(new ListObjectsV2Request()
											.withBucketName(bucketName)
											.withPrefix(path));
						currentSummaryList = result.getObjectSummaries().iterator();
						return nextStream();
					} else if(result.getNextContinuationToken() != null) {
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
			if(objInputStream == null) {
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
	
	private static synchronized <T extends Object> T buildClient(Class<T> typeToBuild,AwsClientBuilder<? extends AwsClientBuilder,T> builder) {
		return buildClient(typeToBuild, builder, AWSREGION.getName());
	}
		
	private static synchronized <T extends Object> T buildClient(Class<T> typeToBuild,AwsClientBuilder<? extends AwsClientBuilder,T> builder,String region) {
		builder.setClientConfiguration(getClientConfiguration());
		builder.setRegion(region);
		builder.setCredentials(AWS_CREDENTIALS());
		return builder.build();
	}

	private AmazonS3 s3Client = null;
	private AWSLogsAsync awsLog = null;
	private AmazonSQS sqs_client = null;
	private AmazonSQSAsync sqs_async_client = null;
	private String AWS_ACCOUNT_ID = "";
	private String SQS_DATA_BUCKET = "";
	private ScheduledExecutorService LOG_SCHEDULER = Executors.newScheduledThreadPool(1); //make sure that only one scheduled action is run at once.
	private boolean pluginBucketInitialized = false;
	private String logSequenceToken = null;
	private final Logger LOG = Logger.getLogger(getClass().getSimpleName());
	private final X2LogFormatter LOG_FORMATTER;
	
	public AmazonAWSRuntime() {
		if(System.getProperty("com.cloudreach.x2.cloud","AWS").equals("AWS")) {
			long start = System.currentTimeMillis();
			String globalQueue = defineQueue(CC.GLOBAL_QUEUE_NAME);
			String AWSAccountId = globalQueue.substring(0,globalQueue.lastIndexOf('/'));
			AWS_ACCOUNT_ID = AWSAccountId.substring(AWSAccountId.lastIndexOf('/')+1);
			SQS_DATA_BUCKET = buildBucketName("X2MSGPAYLOAD");
			
			LOG_FORMATTER = new X2LogFormatter(AWS_ACCOUNT_ID, null, null);
			final boolean printLogs = System.getProperty("cloudreach.connect.logs") == null;
			final String logFilePath = System.getProperty("cloudreach.connect.output");
			final Level logLevel = Level.parse(System.getProperty("cloudreach.connect.log.level","INFO"));
			
			//lets initialise the logger for this runtime
			LOG.setUseParentHandlers(false);
			ConsoleHandler sysLogHandler = new ConsoleHandler() {
				@Override
				public Formatter getFormatter() {
					return LOG_FORMATTER;
				}


				@Override
				protected synchronized void setOutputStream(OutputStream out) throws SecurityException {
					if(printLogs) {
						super.setOutputStream(new CCNullOutputStream()); //To change body of generated methods, choose Tools | Templates.
					} else {
						if(logFilePath == null) {
							super.setOutputStream(out);
						} else {
							try {
								super.setOutputStream(new FileOutputStream(logFilePath, false));
							}catch(FileNotFoundException fnfEx) {}
						}
					}
				}
			};
			sysLogHandler.setLevel(logLevel);
			LOG.addHandler(sysLogHandler);
			LOG.setLevel(logLevel);
			LOG.info(String.format("Discovered AWS Account Id as %s - initialized in %d (ms)", AWS_ACCOUNT_ID, System.currentTimeMillis() - start));
		} else {
			LOG_FORMATTER = null;
		}
	}
	
	private synchronized AmazonSQS getSQS() {
		if(sqs_client == null) {
			sqs_client = buildClient(AmazonSQS.class, AmazonSQSClientBuilder.standard());
		}
		
		return sqs_client;
	}
	
	private synchronized AmazonSQSAsync getSQSAsync() {
		if(sqs_async_client == null) {
			sqs_async_client = buildClient(AmazonSQSAsync.class, AmazonSQSAsyncClientBuilder.standard());
		}
		
		return sqs_async_client;
	}
	
	private synchronized String S3_PLUGIN_BUCKET() {
		if(!pluginBucketInitialized) {
			//we need to figure out of a bucket with the default name exists.
			String pluginBucketRegion = executeFailsafe(x -> getS3().headBucket(new HeadBucketRequest(CCPluginManager.S3_PLUGIN_BUCKET)).getBucketRegion() , null);
			if(pluginBucketRegion == null) { //we could not find the default bucket.
				String altPluginBucket = buildBucketName(CCPluginManager.S3_PLUGIN_BUCKET);
				pluginBucketRegion = executeFailsafe(x -> getS3().headBucket(new HeadBucketRequest(altPluginBucket)).getBucketRegion() , null);
				if(pluginBucketRegion == null) {
					if(executeFailsafe(x -> getS3().createBucket(new CreateBucketRequest(CCPluginManager.S3_PLUGIN_BUCKET, AWSREGION.getName())), null) != null) {
						pluginBucketRegion = AWSREGION.getName();
					} else if(executeFailsafe(x -> getS3().createBucket(new CreateBucketRequest(altPluginBucket, AWSREGION.getName())), null) != null) {
						CCPluginManager.S3_PLUGIN_BUCKET = altPluginBucket; //this means that we need to use an alternative name for the bucket.
						pluginBucketRegion = AWSREGION.getName();
					}
				} else {
					CCPluginManager.S3_PLUGIN_BUCKET = altPluginBucket; //this means that we need to use an alternative name for the bucket.
				}
			}
			BUCKET_PRESENCE_CACHE.add(CCPluginManager.S3_PLUGIN_BUCKET);
			S3_BUCKET_REGION_MAP.put(CCPluginManager.S3_PLUGIN_BUCKET, pluginBucketRegion);
			pluginBucketInitialized = true;
		}
		
		return CCPluginManager.S3_PLUGIN_BUCKET;
	}
	
	private final AtomicBoolean CLOUDWATCH_INITIALIZED = new AtomicBoolean(false);
	
	private void initializeCloudwatchLogging() {
		if(CLOUDWATCH_INITIALIZED.compareAndSet(false, true)) {
			//we need to create a log stream in the aws environment for all of the instances.
			if(awsLog == null) {
				awsLog = buildClient(AWSLogsAsync.class, AWSLogsAsyncClientBuilder.standard());
			}
			executeFailsafe(awsLog::createLogGroup, new CreateLogGroupRequest("CLOUDREACH-X2"));
			executeFailsafe(awsLog::createLogGroup, new CreateLogGroupRequest("CLOUDREACH-CONNECT"));
			executeFailsafe(awsLog::createLogStream, new CreateLogStreamRequest("CLOUDREACH-X2", "CLOUDREACH-X2"));

			try {
				logSequenceToken = awsLog.describeLogStreams(new DescribeLogStreamsRequest("CLOUDREACH-X2")).getLogStreams().iterator().next().getUploadSequenceToken();
			}catch(AWSLogsException logEx) {
				LOG(Level.WARNING, "[%s] I was unable to get the log sequence token because of the error %s", getClass().getSimpleName(), logEx.getMessage());
			}
		}
	}
	
	private final void LOG(Level logLevel,String message,Object... args) {
		LOG.log(logLevel, message, args);
	}
	
	private DynamoDB getDynamoDB(String region) {
		if(region == null || region.equals(AWSREGION.getName())) {
			if(_sys_dynamoDb == null) {
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
		if(s3Client == null) {
			s3Client = buildClient(AmazonS3.class, AmazonS3ClientBuilder.standard());
		}
		
		return s3Client;
	}
	
	private AmazonS3 getS3(String region) {
		if(region == null) {
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
		if(queueId == null) {
			CreateQueueResult instanceQueueResult = getSQS().createQueue(new CreateQueueRequest(queueName).addAttributesEntry(QueueAttributeName.MessageRetentionPeriod.name(), String.valueOf(TimeUnit.HOURS.toSeconds(4))));
			queueId = instanceQueueResult.getQueueUrl();
		} else {
			getSQS().setQueueAttributes(new SetQueueAttributesRequest().withQueueUrl(queueId).addAttributesEntry(QueueAttributeName.MessageRetentionPeriod.name(), String.valueOf(TimeUnit.HOURS.toSeconds(4))));
		}
		
		return queueId;
	}

	@Override
	public void storeModuleList(String moduleJar, List<String> moduleList) throws Throwable {
		getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).putObject(S3_PLUGIN_BUCKET(), moduleJar+".modulelist", CC.toJSONString(moduleList));
	}

	@Override
	public List<String> getModuleList(String moduleJar) throws Throwable {
		try {
			return CC.fromJSONArray(String.class, CC.toString(getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).getObject(S3_PLUGIN_BUCKET(), moduleJar+".modulelist").getObjectContent()));
		}catch(AmazonClientException amzClEx) {}
		return null;
	}

	@Override
	public CloudBlob getModule(String moduleJar) throws IOException {
		//this will eventually kill us because we need to make sure we close the s3obj reference.
		try {
			S3Object s3obj = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).getObject(S3_PLUGIN_BUCKET(), moduleJar);
			return new CloudBlob(moduleJar, s3obj.getObjectMetadata().getLastModified().getTime(), s3obj.getObjectMetadata().getContentLength(), new S3ObjectInputStream(s3obj));
		}catch(AmazonS3Exception s3Ex) {
			if(s3Ex.getStatusCode() == 404) {
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
			return new Long(getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).getObjectAsString(S3_PLUGIN_BUCKET(), moduleJar+".installed")); //this could fail because the module may have never been installed.
		}catch(AmazonClientException awsEx) {
			if(!awsEx.getMessage().contains("Status Code: 404;")) {
					throw awsEx;
			}
			
			return null;
		}
	}

	@Override
	public void setModuleInstallDate(String moduleJar,long installDate) throws IOException {
		getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).putObject(S3_PLUGIN_BUCKET(), moduleJar+".installed", String.valueOf(installDate)); //post an installation reference to s3.
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
				return t.getModuleId() == -1 ? "CLOUDREACH-X2" : ("MODULE-"+t.getModuleId()+"-"+String.valueOf(t.getModuleVersion())+"-"+t.getModuleName()).replaceAll("[^\\.\\-_/#A-Za-z0-9]", "");
			})).entrySet().stream().collect(Collectors.toMap(k -> k.getKey(), v -> 
				v.getValue().stream().map(cl -> new InputLogEvent().withMessage(cl.getMessage()).withTimestamp(cl.getTimestamp())).collect(Collectors.toList())))
			.entrySet().parallelStream().forEach(e -> {
				LOG_SCHEDULER.schedule(() -> {
					try {
						//we can check if the key ends with "CLOUDREACH-CONNECT" and if it does we will use that as the log group.
						String logGroup = e.getKey().endsWith("CLOUDREACH-CONNECT") ? "CLOUDREACH-CONNECT" : "CLOUDREACH-X2";
						LogStream logStream;
						boolean hasStream = false;
						String sequenceToken = null;
						Iterator<LogStream> logStreamItr = awsLog.describeLogStreams(new DescribeLogStreamsRequest(logGroup).withLogStreamNamePrefix(e.getKey())).getLogStreams().iterator();
						while(logStreamItr.hasNext()) {
							logStream = logStreamItr.next();
							if(logStream.getLogStreamName().equals(e.getKey())) {
								hasStream = true;
								sequenceToken = logStream.getUploadSequenceToken();
								break;
							}
						}
						if(!hasStream) {
							executeFailsafe(awsLog::createLogStream, new CreateLogStreamRequest(logGroup, e.getKey()));
						}
						
						awsLog.putLogEvents(new PutLogEventsRequest(logGroup,e.getKey(),e.getValue())
																.withSequenceToken(sequenceToken));
						LOG(Level.FINE, "[%s][%s][%d] Logs sent to the Amazon CloudWatch Service", AmazonAWSRuntime.class.getSimpleName(), e.getKey(), e.getValue().size());
					}catch(Throwable ex) {
						LOG(Level.WARNING, "[%s][%s] Unable to send logs to the Amazon CloudWatch Service. The error was %s", AmazonAWSRuntime.class.getSimpleName(), e.getKey(), "Type:"+ex.getClass().getName()+" Error: "+ex.getMessage());
					}
				}, logRequestCtr.getAndIncrement()*350, TimeUnit.MILLISECONDS); //this will run 350 milliseconds after it is posted (this will guarantee fixed rate delivery to CloudWatch)
			});
		
		
	}

	@Override
	public void deleteQueue(String queueId) {
		getSQS().deleteQueue(queueId);
	}
	
	/**
	 * this method should provide a mechanism for sending messages such
	 * that they can then be retrieved by other members of the cluster, naturally this should map 
	 * to Amazon SQS however this single operation on our lab environment consumes $900 per month
	 * in spend.
	 * 
	 * it would be interesting if there is a good alternative to SQS for this operation.
	 * especially because SQS does not really deliver the performance we are looking for in the first place.
	 * 
	 * what if we instead of using SQS used dynamodb.
	 * 
	 * @param queueId the id of the queue to send a message too
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
			if(jsonMessage.getBytes("UTF-8").length > 250000) { //this is 1k message size.
				String msgId = UUID.randomUUID().toString();
				store(SQS_DATA_BUCKET, msgId, jsonMessage);
				return getSQS().sendMessage(queueId, msgId).getMessageId();
			} else {
				return getSQS().sendMessage(queueId, jsonMessage).getMessageId();
			}
		}catch(UnsupportedEncodingException encEx) {
			return UUID.randomUUID().toString();
		}
	}

	@Override
	public String getQueueId(String queueName) {
		try {
			return getSQS().getQueueUrl(new GetQueueUrlRequest(queueName)).getQueueUrl();
		}catch(com.amazonaws.services.sqs.model.QueueDoesNotExistException queueDoesNotExist) {
			LOG(Level.FINE, "[%s][%s] the queue does not exist on AWS you need to create the queue before asking for it's id", getClass().getSimpleName(), queueName);
			return null;
		}
	}

	@Override
	public List<String> listQueueIds(String location, boolean includeWorkQueues) {
		return getSQS().listQueues("CC-"+(location == null ? "" : location)).getQueueUrls().parallelStream().filter((q) -> { return !q.endsWith("-WORK-QUEUE") || includeWorkQueues; }).collect(Collectors.toList());
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
						CCMessage ccMsg = null;
						int executionCount = 0;
						try {
							//the body will actually be a JSON specified message which will contain some standard data
							String msgId = msg.getBody();
							if(!msgId.startsWith("{")) {
								try {								
									ccMsg = CC.fromJSONString(CCMessage.class, retrieve(SQS_DATA_BUCKET, msgId, String.class));
								}finally {
									delete(SQS_DATA_BUCKET, msgId);
								}
							} else {
								//legacy message processing.
								ccMsg = CC.fromJSONString(CCMessage.class, msg.getBody());
							}
						}catch(IOException jsonEx) {}
						
						//the message will contain a relevant plugin-id so we will use that id to know what we need to run.
						if(ccMsg != null) {
							CCMessage fMsg = ccMsg;
							EVENT_PROCESSOR.submit(() -> {
								processor.processMessage(fMsg);
							});
						}
					});
				}
			}).get();
		}catch(InterruptedException irrEx) {
		}catch(ExecutionException exEx) {
			throw new QueueDoesNotExistException(exEx.getMessage());
		}
		
		return messagesProcessed.value;
	}
	
	private boolean isSystemTable(String tableName) {
		return Arrays.asList(DYNAMO_SYSTEM_TABLES).contains(tableName.toLowerCase());
	}
	
	private String getTableRegion(String tableName) {
		if(isSystemTable(tableName)) {
			return getRegionForS3Bucket(S3_PLUGIN_BUCKET());
		}
		
		return AWSREGION.getName();
	}

	@Override
	public boolean hasNoSQLTable(String tableName) {
		final Holder<Boolean> hasTable = new Holder<>(false);
		getDynamoDB(getTableRegion(tableName)).listTables().forEach((t) -> { if(t.getTableName().equalsIgnoreCase(tableName)) { hasTable.value = true; } });
		
		return hasTable.value;
	}

	@Override
	public void createNoSQLTable(String tableName) {
		Table tbl = getDynamoDB(getTableRegion(tableName)).createTable(tableName, Arrays.asList(new KeySchemaElement("id", KeyType.HASH)), 
													 Arrays.asList(new AttributeDefinition("id", ScalarAttributeType.S)), new ProvisionedThroughput(1L, 1L));
		try { tbl.waitForActive(); } catch(InterruptedException irrEx) {}
	}

	@Override
	public <T> List<T> listNoSQL(String tableName, Class<T> objectType) {
		List<T> retval = new ArrayList<>();
		Table dbTable = getDynamoDB(getTableRegion(tableName)).getTable(tableName);
		dbTable.scan().pages().forEach((p) -> p.forEach(item -> {
			try {
				retval.add(CC.fromJSONString(objectType, item.getString("data")));
			}catch(IOException ioEx) {}
		}));
		
		return retval;
	}

	@Override
	public <T> List<T> queryNoSQL(String tableName, Class<T> objectType, String... pkList) {
		List<T> retval = new ArrayList<>();
		Table dbTable = getDynamoDB(getTableRegion(tableName)).getTable(tableName);
		AtomicInteger ctr = new AtomicInteger(1);
		Map<String,Object> valueMap = Arrays.asList(pkList).stream().collect(Collectors.toMap(pk -> ":p"+ctr.getAndIncrement(), pk -> pk));
		String filterQuery = IntStream.range(1, ctr.get()).mapToObj(i -> "id = :p"+String.valueOf(i)).collect(Collectors.joining(" or "));
		dbTable.query(new QuerySpec()
									.withValueMap(valueMap)
									.withKeyConditionExpression(filterQuery)).forEach(i -> {
										try {
											retval.add(CC.fromJSONString(objectType, i.getString("data")));
										}catch(IOException ioEx) {
											LOG(Level.WARNING, "could not parse json %s", i.getString("data"));
										}
									});
		return retval;
	}

	@Override
	public <T> T getNoSQL(String tableName, String id, Class<T> objectType) throws IOException {
		Table dbTable = getDynamoDB(getTableRegion(tableName)).getTable(tableName);
		Item dbItem = dbTable.getItem(new KeyAttribute("id", id));
		if(dbItem != null) {
			return CC.fromJSONString(objectType, dbItem.getString("data"));
		}
		
		return null;
	}

	@Override
	public void saveNoSQL(String tableName, SystemDBObject... data) {
		TableWriteItems writeJob = new TableWriteItems(tableName);
		Arrays.asList(data).stream().forEach(obj -> writeJob.addItemToPut(new Item().withPrimaryKey("id", obj.getId()).withString("data", CC._safe_toJSONString(obj))));
		getDynamoDB(getTableRegion(tableName)).batchWriteItem(writeJob);
	}

	@Override
	public void watchdog(final String location,final String instanceId,final String instanceQueueUrl) {
		try {
			final Holder<Boolean> hasTable = new Holder<>(false);
			getDynamoDB(null).listTables().forEach((t) -> { if(t.getTableName().equalsIgnoreCase("cloudreach_x2_instances")) { hasTable.value = true; } });
			if(!hasTable.value) {
				//we need to create the table if it does not already exist.
				Table tbl = getDynamoDB(null).createTable("cloudreach_x2_instances", Arrays.asList(new KeySchemaElement("instance_id", KeyType.HASH)), 
														 Arrays.asList(new AttributeDefinition("instance_id", ScalarAttributeType.S)), new ProvisionedThroughput(1L, 1L));
				try { tbl.waitForActive(); } catch(InterruptedException irrEx) {}
				LOG(Level.INFO, "DynamoDB instance monitoring table created successfully");
			}
			//we need to save the fact that we are still alive to dynamodb.
			TableWriteItems writeJob = new TableWriteItems("cloudreach_x2_instances");
			writeJob.addItemToPut(new Item().withPrimaryKey("instance_id", instanceId)
							.withBigInteger("last_seen", BigInteger.valueOf(System.currentTimeMillis()))
							.withString("instance_location", location)
							.withString("instance_url", instanceQueueUrl));
			getDynamoDB(null).batchWriteItem(writeJob);
			//we now need to retrieve a list of all of the instances which exist.
			Table configTable = getDynamoDB(null).getTable("cloudreach_x2_instances");
			HashMap<String,Object> paramList = new HashMap<>();
			paramList.put(":ts",(System.currentTimeMillis() - 7200000));
			IteratorSupport<Item,ScanOutcome> rs = configTable.scan("last_seen < :ts", "instance_id,last_seen,instance_location", null, paramList).iterator();
			TableWriteItems deleteJob = new TableWriteItems("cloudreach_x2_instances");
			boolean hasItems = false;
			while(rs.hasNext()) {
				hasItems = true;
				//now anything that appears here is a queue which is here but really should not be and hence should actually be deleted.
				Item instanceItem = rs.next();
				deleteJob.addPrimaryKeyToDelete(new PrimaryKey("instance_id", instanceItem.getString("instance_id")));
				String queueName = "CC-"+(instanceItem.getString("instance_location") == null ? location : instanceItem.getString("instance_location"))+"-"+instanceItem.getString("instance_id");
				try {
					String queueUrl = getSQS().getQueueUrl(queueName).getQueueUrl();
					getSQS().deleteQueue(queueUrl);
					LOG(Level.WARNING,"detected dead message queue [%s], Queue has been removed url: [%s]",queueName, queueUrl);
				}catch(Throwable ex) {
					LOG(Level.WARNING,"detected dead message queue [%s], but it could not be removed because of the error: %s",queueName, ex.getMessage());
				}
			}
			if(hasItems) {
				getDynamoDB(null).batchWriteItem(deleteJob);
			}
		}catch(Throwable ex) {
			LOG(Level.SEVERE,"Unhandled exception in watchdog process %s",CCError.toString(ex));
		}
	}

	@Override
	public Set<String> listModules() {
		HashSet<String> moduleKeyList = new HashSet<>();
		String marker = null;
		ObjectListing objList;
		while((objList = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).listObjects(new ListObjectsRequest().withBucketName(S3_PLUGIN_BUCKET()).withMaxKeys(500).withMarker(marker))) != null) {
			objList.getObjectSummaries().stream().filter((m) -> { return m.getKey().endsWith(".jar"); }).forEach((s3obj) -> moduleKeyList.add(s3obj.getKey()));
			if(objList.isTruncated()) {
				marker = objList.getNextMarker();
			} else {
				break;
			}
		}
		
		return moduleKeyList;
	}

	@Override
	public void uploadModule(String pluginFile,byte[] pluginBytes) {
		uploadModule(pluginFile,new ByteArrayInputStream(pluginBytes), pluginBytes.length);
	}
	
	@Override
	public void uploadModule(String pluginFile, InputStream in, long moduleSize) {
		ObjectMetadata metaData = new ObjectMetadata();
		metaData.setContentDisposition("filename=\""+pluginFile+"\"");
		metaData.setContentType("application/jar");

		//make sure we don't have other multipart upload requests running for this key
		List<String> uploadsToAbort = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).listMultipartUploads(new ListMultipartUploadsRequest(S3_PLUGIN_BUCKET())).getMultipartUploads().stream()
			.filter(mp -> mp.getKey().equals(pluginFile))
			.map(mp -> mp.getUploadId())
			.collect(Collectors.toList());
		for(String uploadId : uploadsToAbort) {
			getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).abortMultipartUpload(new AbortMultipartUploadRequest(S3_PLUGIN_BUCKET(), pluginFile, uploadId));
			LOG(Level.INFO,"Plugin %s aborting failed multipart upload for this file with id %s", new Object[] { pluginFile,uploadId });
		}
		
		InitiateMultipartUploadRequest initUploadRequest = new InitiateMultipartUploadRequest(S3_PLUGIN_BUCKET(), pluginFile, metaData);
		String uploadId = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).initiateMultipartUpload(initUploadRequest).getUploadId();
		//lets break the upload into 1mb parts
		File uploadDir = new File(System.getProperty("java.io.tmpdir"), UUID.randomUUID().toString());
		uploadDir.mkdirs();
		int numParts = 1;
		final int partLength = 1048576*5;
		byte[] buf = new byte[1024];
		int rb = 0;
		try {
			int partSize = 0;
			FileOutputStream partFile = null;
			List<File> partList = new ArrayList<>();
			while((rb = in.read(buf)) != -1) {
				if(partFile == null) {
					File partFileItem = new File(uploadDir,"part-"+String.valueOf(numParts));
					partList.add(partFileItem);
					partFile = new FileOutputStream(partFileItem);
				}
				partFile.write(buf, 0, rb);
				partSize += rb;
				if(partSize >= partLength) {
					partFile.close();
					numParts++;
					partFile = null;
					partSize = 0;
				}
			}
			if(partFile != null) {
				partFile.close();
			}
			File[] uploadParts = partList.toArray(new File[] {});
			List<PartETag> partTags = new ArrayList<>();
			for(int i = 0; i < uploadParts.length; i++) {
				UploadPartRequest uploadRequest = new UploadPartRequest();
				uploadRequest.setUploadId(uploadId);
				uploadRequest.setPartNumber(i+1);
				uploadRequest.setKey(pluginFile);
				uploadRequest.setBucketName(S3_PLUGIN_BUCKET());
				uploadRequest.setPartSize(uploadParts[i].length());
				uploadRequest.setInputStream(new FileInputStream(uploadParts[i]));
				uploadRequest.setObjectMetadata(metaData);
				uploadRequest.setLastPart(i+1 == uploadParts.length);
				UploadPartResult uploadResponse = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).uploadPart(uploadRequest);
				String etag = uploadResponse.getETag();
				partTags.add(new PartETag(i+1, etag));
				LOG(Level.INFO, "Plugin Part [%d] %s stored in file %s was uploaded and stored successfully, with ETAG: %s part size was %d", new Object[]{i+1,pluginFile, uploadParts[i].getName(), etag, uploadParts[i].length()});
			}
			CompleteMultipartUploadRequest completeUploadRequest = new CompleteMultipartUploadRequest(S3_PLUGIN_BUCKET(), pluginFile, uploadId, partTags);	
			CompleteMultipartUploadResult completeResponse = getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).completeMultipartUpload(completeUploadRequest);
			LOG(Level.INFO,"Plugin %s upload completed. ETAG: %s VERSION %s", new Object[] { pluginFile,completeResponse.getETag(), completeResponse.getVersionId() });
		} catch(IOException ioEx) {
			LOG(Level.WARNING,"[%s] Upload failed as I was unable to read the stream of bytes for the plugin. detailed error was %s", new Object[] {pluginFile, ioEx.getMessage()});
		} catch(AmazonS3Exception s3ex) {
			LOG(Level.WARNING,"[%s] Upload failed. We got the following from Amazon S3 %s. We will abort the upload", new Object[] {pluginFile, s3ex.toString() });
			getS3(getRegionForS3Bucket(S3_PLUGIN_BUCKET())).abortMultipartUpload(new AbortMultipartUploadRequest(S3_PLUGIN_BUCKET(), pluginFile, uploadId));
			throw s3ex;
		} finally {
			CC.deleteDirectory(uploadDir);
		}
	}

	private void provisionConfigTable(DynamoDB dynamoDb) {
		final Holder<Boolean> hasTable = new Holder<>(false);
		dynamoDb.listTables().forEach((t) -> { if(t.getTableName().equalsIgnoreCase("cloudreach_x2_module_configuration")) { hasTable.value = true; } });
		if(!hasTable.value) {
			//we need to create the table if it does not already exist.
			Table tbl = dynamoDb.createTable("cloudreach_x2_module_configuration", Arrays.asList(new KeySchemaElement("id", KeyType.HASH), new KeySchemaElement("key", KeyType.RANGE)), 
													 Arrays.asList(new AttributeDefinition("id", ScalarAttributeType.N),new AttributeDefinition("key", ScalarAttributeType.S)), new ProvisionedThroughput(25L, 25L));
			try { tbl.waitForActive(); } catch(InterruptedException irrEx) {}
			LOG(Level.INFO, "DynamoDB configuration table created successfully");
		}
	}
	
	@Override
	public void setModuleConfiguration(int pluginId,ModuleConfiguration config) {
		DynamoDB dynamoDb = getDynamoDB(getTableRegion("cloudreach_x2_module_configuration"));
		provisionConfigTable(dynamoDb);
		if(config != null && !config.getParameters().isEmpty()) {
			TableWriteItems writeJob = new TableWriteItems("cloudreach_x2_module_configuration");
			for(ModuleConfigurationParameter param : config.getParameters()) {
				switch(param.getOperation()) {
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
	public Map<String,String> getModuleConfiguration(int pluginId) {
		DynamoDB dynamoDb = getDynamoDB(getTableRegion("cloudreach_x2_module_configuration"));
		provisionConfigTable(dynamoDb); //make sure the table is there before we look for values in it.
		Table configTable = dynamoDb.getTable("cloudreach_x2_module_configuration");
		final Map<String,String> config = new HashMap<>();
		configTable.query("id", pluginId).pages().forEach((p) -> { p.forEach((item) -> { config.put(item.getString("key"), item.getString("value")); } );});
		
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
		return name.toLowerCase().replaceAll("[\\_\\.]", "-")+(S3_PLUGIN_BUCKET().endsWith(AWS_ACCOUNT_ID) ? "-"+AWS_ACCOUNT_ID : ""); //all bucket names must be relevant to the current account otherwise there will be conflicts
	}
	
	protected void createS3BucketIfNotPresent(String bucketName) {
		if(!BUCKET_PRESENCE_CACHE.contains(bucketName)) {
			try {
				getS3(getRegionForS3Bucket(bucketName)).createBucket(bucketName);
				BUCKET_PRESENCE_CACHE.add(bucketName);
			}catch(AmazonS3Exception s3ex) {
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
			byte[] byteData = CC.toJSONString(data).getBytes("UTF-8");
			metaData.setContentLength(byteData.length);
			metaData.setContentType("application/json");
			metaData.addUserMetadata("implementation", data.getClass().getName());
			getS3(getRegionForS3Bucket(bucketName)).putObject(bucketName, CC.md5(key)+".datastore", new ByteArrayInputStream(byteData), metaData); //post an installation reference to s3.
		}catch(JsonProcessingException | NoSuchAlgorithmException | UnsupportedEncodingException jsonEx) {
			throw new RuntimeException(jsonEx);
		}
	}
	
	private String getRegionForS3Bucket(final String bucketName) {
		if(!S3_BUCKET_REGION_MAP.containsKey(bucketName)) {
			S3_BUCKET_REGION_MAP.put(bucketName, Optional.of(executeFailsafe(x -> getS3().headBucket(new HeadBucketRequest(bucketName)).getBucketRegion(), null)).orElse(AWSREGION.getName()));
		}
		return S3_BUCKET_REGION_MAP.get(bucketName);
	}
	
	private final Map<String,String> S3_BUCKET_REGION_MAP = new ConcurrentHashMap<>();
	private Pattern bucketParsePattern = Pattern.compile("(The bucket is in this region: )([^\\.]+)");

	@Override
	public <T> T retrieve(String category, String key, Class<T> objType) {
		S3Object obj = null;
		try {
			obj = getS3(getRegionForS3Bucket(buildBucketName(category))).getObject(buildBucketName(category), CC.md5(key)+".datastore");
			String objContent = CC.toString(obj.getObjectContent());
			Class objClass = Class.forName(obj.getObjectMetadata().getUserMetaDataOf("implementation"),false,objType.getClassLoader()); //make sure we are using the right class loader for retrieval
			if(objType.isAssignableFrom(objClass)) {
				return (T)(CC.fromJSONString(objClass, objContent));
			} else {
				return null;
			}
		}catch(AmazonS3Exception s3Ex) {
			if(s3Ex.getMessage().contains("The bucket is in this region:")) {
				Matcher m = bucketParsePattern.matcher(s3Ex.getMessage());
				if(m.find()) {
					String bucketRegion = m.group(2);
					S3_BUCKET_REGION_MAP.put(buildBucketName(category), bucketRegion);
					LOG(Level.INFO, "[%s][%s] was re-routed to region %s", getClass().getSimpleName(), category, bucketRegion);
				}
				
				return retrieve(category, key, objType);
			} else if(!s3Ex.getMessage().contains("Status Code: 404;")) {
				throw s3Ex;
			}
			
			return null;	
		}catch(NoSuchAlgorithmException | ClassNotFoundException | IOException jsonEx) {
			throw new RuntimeException(jsonEx);
		}catch(AmazonClientException awsEx) {
			if(!awsEx.getMessage().contains("Status Code: 404;")) {
					throw awsEx;
			}
			
			return null;	
		}finally {
			if(obj != null) {
				try {
					obj.close();
				}catch(IOException ioex) {}
			}
		}
	}

	@Override
	public void delete(String category, String key) {
		try {
			getS3(getRegionForS3Bucket(buildBucketName(category))).deleteObject(buildBucketName(category), CC.md5(key)+".datastore");
		}catch(NoSuchAlgorithmException ex) {
			throw new RuntimeException(ex);
		}
	}

	@Override
	public void write(String category, String path, String key, InputStream dataStream) {
		//we will want to create a backed temporary file for this because we don't know what the stream length is.
		try {
			File tmpFile = File.createTempFile(UUID.randomUUID().toString(), ".tmp");
			try {
				CC.stream(new FileOutputStream(tmpFile), dataStream, false);
				ObjectMetadata metaData = new ObjectMetadata();
				metaData.setContentLength(tmpFile.length());
				String bucketName = buildBucketName(category);
				createS3BucketIfNotPresent(bucketName);
				getS3(getRegionForS3Bucket(bucketName)).putObject(bucketName, path+"/"+key, new FileInputStream(tmpFile), metaData);
			}finally {
				tmpFile.delete();
			}
		}catch(IOException ioex) {
			throw new RuntimeException(ioex);
		}
	}

	@Override
	public InputStream read(String category, String path, String key) {
		try {
			//we need to implement this such that it will temporarily spool to a file and then from that file return a stream reference
			return new S3ObjectInputStream(getS3(getRegionForS3Bucket(buildBucketName(category))).getObject(buildBucketName(category), path+"/"+key));
		}catch(AmazonS3Exception amazonEx) {
			return null;
		}
	}

	@Override
	public void remove(String category, String path, String key) {
		final String bucketName = buildBucketName(category);
		final AmazonS3 s3 = getS3(getRegionForS3Bucket(bucketName));
		if(s3.doesBucketExistV2(bucketName) && s3.doesObjectExist(bucketName, path == null ? key : path+"/"+key)) {
			s3.deleteObject(new DeleteObjectRequest(bucketName, path == null ? key : path+"/"+key));
		}
	}

	@Override
	public String getQueueName(String queueId) {
		return queueId.substring(queueId.lastIndexOf('/')+1);
	}
}
