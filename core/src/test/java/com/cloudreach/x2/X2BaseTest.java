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

import com.cloudreach.connect.x2.AbstractX2;
import com.cloudreach.connect.x2.CC;
import com.cloudreach.connect.x2.api.KeyValue;
import com.cloudreach.connect.x2.api.Module;
import com.cloudreach.connect.x2.api.ModuleLimit;
import com.cloudreach.connect.x2.internal.model.CCMessage;
import com.cloudreach.connect.x2.internal.model.CloudBlob;
import com.cloudreach.connect.x2.internal.model.CloudLogEvent;
import com.cloudreach.connect.x2.internal.model.CloudProvider;
import com.cloudreach.connect.x2.internal.model.CloudQueueProcessor;
import com.cloudreach.connect.x2.internal.model.CloudRuntime;
import com.cloudreach.connect.x2.internal.model.ModuleConfiguration;
import com.cloudreach.connect.x2.internal.model.QueueDoesNotExistException;
import com.cloudreach.connect.x2.internal.model.SystemDBObject;
import com.cloudreach.connect.x2.internal.model.X2ApplicationMetaData;
import com.cloudreach.connect.x2.runtime.MemoryRuntime;
import com.cloudreach.connect.x2.sys.CCHTTPConnector.MODE;
import com.cloudreach.connect.x2.sys.CCPluginManager;
import com.cloudreach.connect.x2.sys.internal.Util;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.AfterClass;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public abstract class X2BaseTest {
	public static abstract class MockRuntime implements CloudRuntime {

		@Override
		public String defineQueue(String queueName) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void storeModuleList(String moduleJar, List<String> moduleList) throws Throwable {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public List<String> getModuleList(String moduleJar) throws Throwable {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public CloudBlob getModule(String moduleJar) throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Long getModuleInstallDate(String moduleJar) throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void setModuleInstallDate(String moduleJar, long installDate) throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void log(List<CloudLogEvent> eventList) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Set<String> listModules() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void uploadModule(String pluginFile, byte[] pluginBytes) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void uploadModule(String pluginFile, InputStream in, long moduleSize) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void deleteQueue(String queueId) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String sendMessage(String queueId, String jsonMessage) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getQueueId(String queueName) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public List<String> listQueueIds(String location) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public int pollQueue(String queueId, CloudQueueProcessor processor) throws QueueDoesNotExistException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public boolean hasNoSQLTable(String tableName) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void createNoSQLTable(String tableName) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void dropNoSQLTable(String tableName) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public <T> List<T> listNoSQL(String tableName, Class<T> objectType) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public <T> List<T> queryNoSQL(String tableName, Class<T> objectType, String... pkList) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public <T> T getNoSQL(String tableName, String id, Class<T> objectType) throws IOException {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void saveNoSQL(String tableName, SystemDBObject... data) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void deleteNoSQL(String tableName, SystemDBObject... data) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void watchdog(final String location,final String instanceId,final String instanceQueueUrl) {}

		@Override
		public void setModuleConfiguration(int pluginId, ModuleConfiguration config) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Map<String, String> getModuleConfiguration(int pluginId) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void store(String key, Object data) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public <T> T retrieve(String key, Class<T> objType) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void store(String category, String key, Object data) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public <T> T retrieve(String category, String key, Class<T> objType) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void delete(String category, String key) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void write(String category, String path, String key, InputStream dataStream) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public InputStream read(String category, String path, String key) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public List<String> listQueueIds(String location, boolean includeWorkQueues) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getDefaultCategory() {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public Stream<InputStream> readAll(String category, String path) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public void remove(String category, String path, String key) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}

		@Override
		public String getQueueName(String queueId) {
			throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
		}
	}
	
	public static class TestX2Server extends AbstractX2 {
		public TestX2Server(final String UUID_FILE_NAME,final String location,final int httpPort,final String pluginWhitelist) {
			super(location,httpPort,MODE.HTTPS,pluginWhitelist,"220,2699,25700,25699,850000-900000,30",50,true,true,null,Level.INFO,UUID_FILE_NAME);
		}
	}
	
	protected synchronized static void registerServerInstance(X2BaseTest serverInstance) {
		X2BaseTest.serverInstance = serverInstance;
		X2BaseTest.serverInstance.init();
	}
	
	protected static volatile X2BaseTest serverInstance = new X2BaseTest(true) {};
	
	protected TestX2Server x2server = null;
	
	protected X2BaseTest(boolean serverInstance) {
		System.setProperty("cloudreach.connect.logs","true");
		System.setProperty("com.cloudreach.x2.cloud","AWS");
		System.setProperty("com.cloudreach.azure.msg.model","QUEUE");
		System.setProperty("com.cloudreach.azure.storage","x2storage");
		System.setProperty("cloudreach.connect.log.level","INFO");
		System.setProperty("com.cloudreach.connect.plugin.whitelist","21");
	}
	
	protected X2BaseTest() { init(); this.x2server = serverInstance.x2server; }
	
	private synchronized void init() {
		if(serverInstance.x2server == null) {
			if(this instanceof X2GSMTest && !(serverInstance instanceof X2GSMTest)) {
				serverInstance = new X2GSMTest("TEST1","TEST2") {};
			}
			serverInstance.startX2();
			CC.INSTANCE_ID = serverInstance.x2server.getINSTANCE_ID();
		}
	}
	
	@AfterClass
	public static void end() {
		serverInstance.stopX2();
		serverInstance = new X2BaseTest(true) {};
		//we also need to resent the memory runtime instance.
		((MemoryRuntime)CloudProvider.MEMORY.getRuntime()).reset();
	}
	
	public void startX2() {
		x2server = new TestX2Server("X2UUID_"+UUID.randomUUID().toString(),"AWS",8080,"20");
		Util.B(null, x -> x2server.start());
	}
	
	public void stopX2() {
		Util.B(null, x -> x2server.stop());
		x2server = null;
	}
	
	protected void uploadModule(int moduleId,double moduleVersion,String pluginName,InputStream pluginDataStream) throws Throwable {
		CCPluginManager.PluginManagerModule pluginManagerMod = (CCPluginManager.PluginManagerModule)x2server.getPluginManager().getModuleByClassName(0, CCPluginManager.PluginManagerModule.class.getName()).getModule();
		x2server.getPluginManager().runWithModuleContext(Void.class, x -> {
			pluginManagerMod.uploadModule(moduleId, moduleVersion, pluginName, pluginDataStream);
			return null;
		});
	}
	
	protected void processBatch(int moduleId,double moduleVersion, String moduleClass) throws InterruptedException {
		x2server.getPluginManager().clearBatchExecutionMap();
		//let's check if the batch has already been run by the scheduler (if it has not it will be in about a second anyway)
		long lastExecutedOn = x2server.getPluginManager().getLastLaunchedModuleEventOnGSM(moduleId, moduleVersion, moduleClass);
		if(lastExecutedOn == 0) {
			do {
				lastExecutedOn = x2server.getPluginManager().getLastLaunchedModuleEventOnGSM(moduleId, moduleVersion, moduleClass);
				TimeUnit.SECONDS.sleep(1);
			}while(lastExecutedOn == 0);
		} else {
			x2server.runBatch(moduleId, moduleVersion, moduleClass);
		}
		Util.B(null, x -> Thread.sleep(TimeUnit.SECONDS.toMillis(2)));
	}
	
	protected void uploadModule(int moduleId,double moduleVersion,String pluginName,Class... moduleClassList) throws Throwable {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		Util.createJar(byteOut, moduleClassList);
		final byte[] jarBytes = byteOut.toByteArray();
		
		//we now need to upload this jar file to the GSM cluster.
		uploadModule(moduleId, moduleVersion, pluginName, new ByteArrayInputStream(jarBytes));
	}
	
	protected void sendMessage(int moduleId,double moduleVersion,Class<? extends Module> moduleClass,String location,KeyValue... attributes) throws Throwable {
		x2server.getPluginManager().runWithModuleContext(Void.class, x -> {
			CCMessage msg = new CCMessage();
			msg.setModuleClass(moduleClass.getName());
			msg.setPluginId(moduleId);
			msg.setPluginVersion(moduleVersion);
			if(attributes != null) {
				msg.getAttributes().putAll(Arrays.asList(attributes)
					.stream()
					.filter(kv -> kv != null)
					.collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue))
				);
			}
			msg.send(location);
			return null;
		});
	}
}
