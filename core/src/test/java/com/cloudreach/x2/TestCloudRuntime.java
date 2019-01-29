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
package com.cloudreach.x2;

import com.cloudreach.connect.x2.CC;
import com.cloudreach.connect.x2.internal.model.CCMessage;
import com.cloudreach.connect.x2.internal.model.CloudBlob;
import com.cloudreach.connect.x2.internal.model.CloudProvider;
import com.cloudreach.connect.x2.internal.model.CloudQueueProcessor;
import com.cloudreach.connect.x2.internal.model.CloudRuntime;
import com.cloudreach.connect.x2.internal.model.ModuleConfiguration;
import com.cloudreach.connect.x2.internal.model.ModuleConfigurationOperation;
import com.cloudreach.connect.x2.internal.model.ModuleConfigurationParameter;
import com.cloudreach.connect.x2.internal.model.QueueDoesNotExistException;
import com.cloudreach.connect.x2.sys.CCPluginManager;
import com.cloudreach.connect.x2.sys.auth.CCUser;
import com.cloudreach.connect.x2.sys.internal.Util;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.Test;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestCloudRuntime extends X2BaseTest {
	
	public TestCloudRuntime() throws Throwable { super(); }
	/**
	 * the scope of this test to to make sure the amazon runtime can correctly store and retrieve a string value.
	 */
	@Test
	public void testStoreRetrieveString() throws Throwable {
		//test functionality on amazon (aws)
		for(CloudProvider provider : CloudProvider.values()) {
			testStoreRetrieveString(provider.getRuntime());
		}
	}
	
	protected void testStoreRetrieveString(CloudRuntime runtime) throws Throwable {
		try {
			String keyValue = "String value";
			runtime.store("x2_test_testStoreRetrieveString", keyValue);
			Assert.assertEquals(keyValue, runtime.retrieve("x2_test_testStoreRetrieveString", String.class));
			keyValue = CC.toJSONString(System.getProperties());
			runtime.store("x2_test_testStoreRetrieveString", keyValue);
			Assert.assertEquals(keyValue, runtime.retrieve("x2_test_testStoreRetrieveString", String.class));
			ObjectMapper mapper = new ObjectMapper();
			HashMap<String,String> map = new HashMap<>();
			map.put("key","value");
			keyValue = mapper.writeValueAsString(map);
			runtime.store("x2_test_testStoreRetrieve[String]", keyValue);
			Assert.assertEquals(keyValue, runtime.retrieve("x2_test_testStoreRetrieve[String]", String.class));
		}finally {
			runtime.delete(CCPluginManager.S3_PLUGIN_BUCKET, "x2_test_testStoreRetrieveString");
			runtime.delete(CCPluginManager.S3_PLUGIN_BUCKET, "x2_test_testStoreRetrieve[String]");
		}
	}
	
	@Test
	public void testDeleteNoSQL() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testDeleteNoSQL(provider.getRuntime());
		}
	}
	
	protected void testDeleteNoSQL(CloudRuntime runtime) throws Throwable {
		//we need to create a temporary table to run the test.
		final String noSqlTable = "X2-TEST-NOSQL";
		CCUser user = new CCUser();
		user.setAdmin(true);
		user.setGroupIds(Arrays.asList("1","2"));
		user.setPassword("password");
		user.setUsername("username");
		
		runtime.createNoSQLTable(noSqlTable);
		try {
			runtime.saveNoSQL(noSqlTable, user);
			List<CCUser> userList = runtime.listNoSQL(noSqlTable, CCUser.class);
			assertEquals(1, userList.size());
			runtime.deleteNoSQL(noSqlTable, user);
			userList = runtime.listNoSQL(noSqlTable, CCUser.class);
			assertEquals(0, userList.size());
		}finally {
			runtime.dropNoSQLTable(noSqlTable);
		}
	}
	
	@Test
	public void testDropNoSQLTable() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testDropNoSQLTable(provider.getRuntime());
		}
	}
	
	protected void testDropNoSQLTable(CloudRuntime runtime) throws Throwable {
		final String noSqlTable = "X2-TEST-NOSQL";
		runtime.createNoSQLTable(noSqlTable);
		assertTrue(runtime.hasNoSQLTable(noSqlTable));
		runtime.dropNoSQLTable(noSqlTable);
		assertFalse(runtime.hasNoSQLTable(noSqlTable));
	}
	
	@Test
	public void testQueryNoSQL() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testQueryNoSQL(provider.getRuntime());
		}
	}
	
	protected void testQueryNoSQL(CloudRuntime runtime) throws Throwable {
		final String noSqlTable = "X2-TEST-NOSQL";
		runtime.createNoSQLTable(noSqlTable);
		try {
			CCUser user = new CCUser();
			user.setAdmin(true);
			user.setGroupIds(Arrays.asList("1","2"));
			user.setPassword("password");
			user.setUsername("username");
			
			runtime.saveNoSQL(noSqlTable, user);
			List<CCUser> userList = runtime.queryNoSQL(noSqlTable, CCUser.class, user.getId());
			assertNotNull(userList);
			assertEquals(1, userList.size());
			assertEquals(CC.toJSONString(user),CC.toJSONString(userList.get(0)));
		}finally {
			runtime.dropNoSQLTable(noSqlTable);
		}
	}
	
	@Test
	public void testSaveNoSQL() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testSaveNoSQL(provider.getRuntime());
		}
	}
	
	protected void testSaveNoSQL(CloudRuntime runtime) throws Throwable {
		final String noSqlTable = "X2-TEST-NOSQL";
		runtime.createNoSQLTable(noSqlTable);
		try {
			CCUser user = new CCUser();
			user.setAdmin(true);
			user.setGroupIds(Arrays.asList("1","2"));
			user.setPassword("password");
			user.setUsername("username");
			
			runtime.saveNoSQL(noSqlTable, user);
			List<CCUser> userList = runtime.queryNoSQL(noSqlTable, CCUser.class, user.getId());
			assertNotNull(userList);
			assertEquals(1, userList.size());
			assertEquals(CC.toJSONString(user),CC.toJSONString(userList.get(0)));
			
			user.setAdmin(false);
			runtime.saveNoSQL(noSqlTable, user);
			userList = runtime.queryNoSQL(noSqlTable, CCUser.class, user.getId());
			assertFalse(userList.get(0).isAdmin());
		}finally {
			runtime.dropNoSQLTable(noSqlTable);
		}
	}
	
	@Test
	public void testGetQueueId() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testGetQueueId(provider.getRuntime());
		}
	}
	
	protected void testGetQueueId(CloudRuntime runtime) throws Throwable {
		//we will have 3 different types of queues that the system will use. these are as follows.
		//1. a queue which identifies the instance CC-[LOCATION]-[INSTANCE_ID]
		//2. a queue which identifies the location CC-[LOCATION]-[WORK-QUEUE]
		//3. a global queue CC-GLOBAL-WORK-QUEUE
		
		//step 1: lets test for all the cases in which a queue should not exist. (expected result will be null)
		final String GLOBAL_QUEUE = "CC-GLOBAL-WORK-QUEUE";
		if(runtime.getQueueId(GLOBAL_QUEUE) == null) {
			runtime.defineQueue(GLOBAL_QUEUE); //if the global queue does not exist we should define it.
		}
		
		assertNotNull(runtime.getQueueId(GLOBAL_QUEUE)); //after it was defined it should of course exist
		
		final String EXIST_INSTANCE_QUEUE = "CC-UNITTESTVALID-"+UUID.randomUUID().toString();
		try {
			runtime.getQueueId(EXIST_INSTANCE_QUEUE); //this should not throw an error.
			
			final String instanceQueueId = runtime.defineQueue(EXIST_INSTANCE_QUEUE);
			//we need to wait for these queues to appear.
			int ctr = 0;
			do {
				if(runtime.listQueueIds("UNITTESTVALID").stream().anyMatch(qId -> qId.equalsIgnoreCase(instanceQueueId))) {
					Logger.getAnonymousLogger().info("The queue has been created and is ready to be tested");
					break;
				}
				Thread.sleep(TimeUnit.SECONDS.toMillis(1)); //wait 10 seconds for the queues to become available (may not be necessary for all runtimes).
				ctr++;
				if(ctr == 60) {
					Logger.getAnonymousLogger().info("The queues that needed to be tested were never created. This test will fail.");
				}
			}while(ctr < 60);
			assertEquals(instanceQueueId, runtime.getQueueId(EXIST_INSTANCE_QUEUE));
		}finally {
			//we need to delete the queues after the test.
			try {
				runtime.deleteQueue(runtime.getQueueId(EXIST_INSTANCE_QUEUE));
			}catch(Throwable ex) {}
		}
	}
	
	@Test
	public void testGetNoSQL() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testGetNoSQL(provider.getRuntime());
		}
	}
	
	protected void testGetNoSQL(CloudRuntime runtime) throws Throwable {
		try {
			runtime.getNoSQL("RANDOM_TABLE", "random_id", CCUser.class);
			assertFalse(runtime.getClass().getSimpleName(), true);
		}catch(Throwable ex) {
			assertNotNull(runtime.getClass().getSimpleName(), ex); //we expect an exception if we ask to retrieve an invalid bit of data.
		}
		
		//however if we ask for a value which does not exist in a table which does exist we expect the method to run successfully but return null.
		try {
			runtime.createNoSQLTable("X2-TEST-NOSQL");
			assertNull(runtime.getClass().getSimpleName(),runtime.getNoSQL("X2-TEST-NOSQL", "random_id", CCUser.class));
		}finally {
			runtime.dropNoSQLTable("X2-TEST-NOSQL");
		}
	}
	
	@Test
	public void testGetQueueName() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testGetQueueName(provider.getRuntime());
		}
	}
	
	protected void testGetQueueName(CloudRuntime runtime) throws Throwable {
		final String TEST_QUEUE_NAME = "CC-UNITTEST-"+UUID.randomUUID().toString();
		String testQueueId = null;
		try {
			testQueueId = runtime.defineQueue(TEST_QUEUE_NAME);
			assertEquals(TEST_QUEUE_NAME.toUpperCase(), runtime.getQueueName(testQueueId).toUpperCase());
		}finally {
			if(testQueueId != null) {
				runtime.deleteQueue(testQueueId);
			}
		}
	}
	
	@Test
	public void testUploadModule() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testUploadModule(provider.getRuntime());
		}
	}
	
	protected void testUploadModule(CloudRuntime runtime) throws Throwable {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		Util.createJar(byteOut, TestCloudRuntime.class);
		final byte[] jarBytes = byteOut.toByteArray();
		
		runtime.uploadModule("60000_Test-1-1.0.jar", jarBytes);
		CloudBlob blob = runtime.getModule("60000_Test-1-1.0.jar");
		assertNotNull(blob);
		assertArrayEquals(jarBytes, blob.getData());
		
		//we also need to be able to remove the module
		runtime.removeModule("60000_Test-1-1.0.jar");
		assertNull(runtime.getModule("60000_Test-1-1.0.jar"));
	}
	
	@Test
	public void testModuleConfiguration() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testModuleConfiguration(provider.getRuntime());
		}
	}
	
	public void testModuleConfiguration(CloudRuntime runtime) throws Throwable {
		Map<String,String> config = runtime.getModuleConfiguration(60000);
		assertTrue(config.isEmpty());
		try {
			ModuleConfiguration cfg = new ModuleConfiguration();
			ModuleConfigurationParameter param = new ModuleConfigurationParameter();
			param.setKey("test");
			param.setValue("test");
			param.setOperation(ModuleConfigurationOperation.upsert);
			cfg.getParameters().add(param);
			runtime.setModuleConfiguration(60000, cfg); //remove all parameters which were added.
			config = runtime.getModuleConfiguration(60000);
			assertEquals(1,config.size());
			assertTrue(config.containsKey("test"));
			assertEquals("test",config.get("test"));
			param.setValue("test2");
			runtime.setModuleConfiguration(60000, cfg); //remove all parameters which were added.
			config = runtime.getModuleConfiguration(60000);
			assertEquals(1,config.size());
			assertTrue(config.containsKey("test"));
			assertEquals("test2",config.get("test"));
			
			ModuleConfiguration cfg2 = new ModuleConfiguration();
			ModuleConfigurationParameter param2 = new ModuleConfigurationParameter();
			param2.setKey("test2");
			param2.setValue("test");
			param2.setOperation(ModuleConfigurationOperation.upsert);
			cfg2.getParameters().add(param2);
			runtime.setModuleConfiguration(60000, cfg2); //remove all parameters which were added.
			config = runtime.getModuleConfiguration(60000);
			assertEquals(runtime.getClass().getSimpleName(),2,config.size());
			assertTrue(config.containsKey("test") && config.containsKey("test2"));
			
			param2.setOperation(ModuleConfigurationOperation.delete);
			param.setOperation(ModuleConfigurationOperation.delete);
			cfg.getParameters().add(param2);
			runtime.setModuleConfiguration(60000, cfg); //remove all parameters which were added.
			config = runtime.getModuleConfiguration(60000);
			assertTrue(config.isEmpty());
		}finally {
			if(!config.isEmpty()) {
				ModuleConfiguration newConfig = new ModuleConfiguration();
				config.entrySet().forEach(e -> { 
					ModuleConfigurationParameter param = new ModuleConfigurationParameter();
					param.setKey(e.getKey());
					param.setValue(e.getValue());
					param.setOperation(ModuleConfigurationOperation.delete);
					newConfig.getParameters().add(param);
				});
				runtime.setModuleConfiguration(60000, newConfig); //remove all parameters which were added.
			}
		}
	}
	
	@Test
	public void testPollQueue() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testPollQueue(provider.getRuntime());
		}
	}
	
	protected void testPollQueue(CloudRuntime runtime) throws Throwable {
		//create a queue
		final String TEST_QUEUE_NAME = "CC-UNITTEST-"+UUID.randomUUID().toString();
		final String QUEUE_ID = runtime.defineQueue(TEST_QUEUE_NAME);
		try {
			//send a message to the queue.
			CCMessage msg = new CCMessage();
			msg.setSourceInstance(QUEUE_ID);
			List<CCMessage> msgList = new CopyOnWriteArrayList<>();
			runtime.sendMessage(QUEUE_ID, Util.toJSONString(msg));
			CountDownLatch latch = new CountDownLatch(1);
			int numMsg = runtime.pollQueue(QUEUE_ID, new CloudQueueProcessor() {
				@Override
				public void processMessage(CCMessage msg) {
					msgList.add(msg);
					latch.countDown();
				}
			});
			assertEquals(runtime.getClass().getSimpleName(),1,numMsg);
			if(numMsg > 0) {
				latch.await();
			}
			assertEquals(runtime.getClass().getSimpleName(),1,msgList.size());
			CCMessage retrievedMsg = msgList.get(0);
			assertEquals(runtime.getClass().getSimpleName(),msg.getId(),retrievedMsg.getId());
			
			//now attempt to poll an invalid queue
			try {
				runtime.pollQueue(QUEUE_ID+"_fake", (msg1) -> {});
				assertFalse(runtime.getClass().getSimpleName(),true);
			}catch(QueueDoesNotExistException ex) {
				assertNotNull(runtime.getClass().getSimpleName(),ex);
			}
		}finally {
			runtime.deleteQueue(TEST_QUEUE_NAME);
		}
	}
	
	@Test
	public void testReadWriteInputStream() throws Throwable {
		for(CloudProvider provider : CloudProvider.values()) {
			testReadWriteInputStream(provider.getRuntime());
		}
	}
	
	protected void testReadWriteInputStream(CloudRuntime runtime) throws Throwable {
		final byte[] data = new byte[] {1,2,3};
		runtime.write("test", "test", new ByteArrayInputStream(data));
		try {
			assertArrayEquals(data,Util.toByteArray(runtime.read("test", "test")));
		}finally {
			runtime.remove("test","test");
		}
	}
}
