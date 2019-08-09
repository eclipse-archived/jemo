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

import org.eclipse.jemo.api.KeyValue;
import org.eclipse.jemo.api.Module;
import org.eclipse.jemo.internal.model.*;
import org.eclipse.jemo.runtime.MemoryRuntime;
import org.eclipse.jemo.sys.JemoPluginManager;
import org.eclipse.jemo.sys.internal.Util;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.junit.AfterClass;

import static org.eclipse.jemo.api.JemoParameter.*;
import static org.eclipse.jemo.internal.model.AmazonAWSRuntime.AWS_REGION_PROP;
import static org.eclipse.jemo.internal.model.GCPRuntime.GCP_REGION_PROP;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public abstract class JemoBaseTest {

	public static class TestJemoServer extends AbstractJemo {
		public TestJemoServer(final String UUID_FILE_NAME, final String location, final int httpPort, final String pluginWhitelist) {
			super(location,httpPort,httpPort+1,pluginWhitelist,"220,2699,25700,25699,850000-900000,30",50,true,true,null,Level.INFO,UUID_FILE_NAME);
		}
	}
	
	protected synchronized static void registerServerInstance(JemoBaseTest serverInstance) {
		JemoBaseTest.serverInstance = serverInstance;
		JemoBaseTest.serverInstance.init();
	}
	
	protected static volatile JemoBaseTest serverInstance = new JemoBaseTest(true) {};
	
	protected TestJemoServer jemoServer = null;
	
	protected JemoBaseTest(boolean serverInstance) {
		System.setProperty(AWS_REGION_PROP,"eu-west-1");
		System.setProperty(GCP_REGION_PROP,"europe-west2-a");
		System.setProperty(LOG_LOCAL.label(),"true");
		System.setProperty(CLOUD.label(),"MEMORY");
		System.setProperty("eclipse.jemo.azure.msg.model","QUEUE");
		System.setProperty(LOG_LEVEL.label(),"INFO");
		System.setProperty(MODULE_WHITELIST.label(),"21");

		System.setProperty(MemoryRuntime.USER, "test");
		System.setProperty(MemoryRuntime.PASSWORD, "test");
	}
	
	protected JemoBaseTest() {
		System.setProperty(MemoryRuntime.USER, "test");
		System.setProperty(MemoryRuntime.PASSWORD, "test");

		init();
		this.jemoServer = serverInstance.jemoServer;
	}
	
	private synchronized void init() {
		if(serverInstance.jemoServer == null) {
			if(this instanceof JemoGSMTest && !(serverInstance instanceof JemoGSMTest)) {
				serverInstance = new JemoGSMTest("TEST1","TEST2") {};
			}
			serverInstance.startJemo();
			Jemo.INSTANCE_ID = serverInstance.jemoServer.getINSTANCE_ID();
		}
	}
	
	@AfterClass
	public static void end() {
		serverInstance.stopJemo();
		serverInstance = new JemoBaseTest(true) {};
		//we also need to resent the memory runtime instance.
		((MemoryRuntime)CloudProvider.MEMORY.getRuntime()).reset();
	}
	
	public void startJemo() {
		jemoServer = new TestJemoServer("JEMOUUID_"+UUID.randomUUID().toString(),"AWS",8080,"20");
		try {
			Util.B(null, x -> jemoServer.start());
		}catch(Throwable ex) {
			ex.printStackTrace();
		}
	}
	
	public void stopJemo() {
		Util.B(null, x -> jemoServer.stop());
		jemoServer = null;
	}
	
	protected void uploadPlugin(int pluginId, double pluginVersion, String pluginName, InputStream pluginDataStream) throws Throwable {
		JemoPluginManager.PluginManagerModule pluginManagerMod = (JemoPluginManager.PluginManagerModule) jemoServer.getPluginManager().loadModuleByClassName(0, JemoPluginManager.PluginManagerModule.class.getName()).getModule();
		jemoServer.getPluginManager().runWithModuleContext(Void.class, x -> {
			pluginManagerMod.uploadPlugin(pluginId, pluginVersion, pluginName, pluginDataStream);
			return null;
		});
	}
	
	protected void processBatch(int moduleId,double moduleVersion, String moduleClass) throws InterruptedException {
		jemoServer.getPluginManager().clearBatchExecutionMap();
		//let's check if the batch has already been run by the scheduler (if it has not it will be in about a second anyway)
		long lastExecutedOn = jemoServer.getPluginManager().getLastLaunchedModuleEventOnGSM(moduleId, moduleVersion, moduleClass);
		if(lastExecutedOn == 0) {
			do {
				lastExecutedOn = jemoServer.getPluginManager().getLastLaunchedModuleEventOnGSM(moduleId, moduleVersion, moduleClass);
				TimeUnit.SECONDS.sleep(1);
			}while(lastExecutedOn == 0);
		} else {
			jemoServer.runBatch(moduleId, moduleVersion, moduleClass);
		}
		Util.B(null, x -> Thread.sleep(TimeUnit.SECONDS.toMillis(2)));
	}
	
	protected void uploadPlugin(int pluginId, double pluginVersion, String pluginName, 
			
			Class... moduleClassList) throws Throwable {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		Util.createJar(byteOut, moduleClassList);
		final byte[] jarBytes = byteOut.toByteArray();
		
		//we now need to upload this jar file to the GSM cluster.
		uploadPlugin(pluginId, pluginVersion, pluginName, new ByteArrayInputStream(jarBytes));
	}
	
	protected void sendMessage(int moduleId,double moduleVersion,Class<? extends Module> moduleClass,String location,KeyValue... attributes) throws Throwable {
		jemoServer.getPluginManager().runWithModuleContext(Void.class, x -> {
			JemoMessage msg = new JemoMessage();
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
