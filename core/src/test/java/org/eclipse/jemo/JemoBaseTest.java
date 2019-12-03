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
import org.eclipse.jemo.sys.auth.JemoAuthentication;
import org.eclipse.jemo.sys.auth.JemoGroup;
import org.eclipse.jemo.sys.auth.JemoUser;
import org.eclipse.jemo.sys.internal.JarEntry;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.jemo.sys.microprofile.MicroProfileConfigSource;
import org.eclipse.jemo.sys.microprofile.MicroProfileConfigSourceTest.FixedTestModule;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

import javax.servlet.ReadListener;
import javax.servlet.ServletInputStream;

import org.junit.AfterClass;

import com.fasterxml.jackson.core.JsonProcessingException;

import static org.eclipse.jemo.api.JemoParameter.*;
import static org.eclipse.jemo.internal.model.AmazonAWSRuntime.AWS_REGION_PROP;
import static org.eclipse.jemo.internal.model.GCPRuntime.GCP_REGION_PROP;
import static org.eclipse.jemo.sys.JemoPluginManager.PLUGIN_ID;
import static org.eclipse.jemo.sys.JemoPluginManager.PLUGIN_VERSION;

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
	
	protected JemoPluginManager.PluginManagerModule getPluginManagerModule() {
		return (JemoPluginManager.PluginManagerModule) jemoServer.getPluginManager().loadModuleByClassName(0, JemoPluginManager.PluginManagerModule.class.getName()).getModule();
	}
	
	protected void uploadPlugin(int pluginId, double pluginVersion, String pluginName, InputStream pluginDataStream) throws Throwable {
		JemoPluginManager.PluginManagerModule pluginManagerMod = getPluginManagerModule();
		jemoServer.getPluginManager().runWithModuleContext(Void.class, x -> {
			pluginManagerMod.uploadPlugin(pluginId, pluginVersion, pluginName, pluginDataStream);
			return null;
		});
		//we should now wait at most 30 seconds for the application to appear in the registration list.
		int ctr = 30;
		while(!jemoServer.getPluginManager().getApplicationList().stream()
				.anyMatch(app -> PLUGIN_ID(app.getId()) == pluginId && pluginVersion == PLUGIN_VERSION(app.getId())) && ctr != 0) {
			Thread.sleep(1000);
			ctr--;
		}
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
		uploadPlugin(pluginId, pluginVersion, pluginName, Arrays.asList(moduleClassList)
				.stream()
				.map(cls -> Util.F(null, x -> new JarEntry(cls)))
				.toArray(JarEntry[]::new)
				);
	}
	
	protected void uploadPlugin(int pluginId, double pluginVersion, String pluginName, 
			JarEntry... moduleClassList) throws Throwable {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		Util.createJar(byteOut, moduleClassList);
		final byte[] jarBytes = byteOut.toByteArray();
		
		//we now need to upload this jar file to the GSM cluster.
		uploadPlugin(pluginId, pluginVersion, pluginName, new ByteArrayInputStream(jarBytes));
	}
	
	protected void startFixedProcesses() throws InterruptedException {
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
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
	
	protected ServletInputStream toInputStream(Object obj) throws JsonProcessingException {
		return toInputStream(Util.toJSONString(obj).getBytes(Util.UTF8_CHARSET));
	}
	
	protected ServletInputStream toInputStream(byte[] data) {
		final ByteArrayInputStream configStream = new ByteArrayInputStream(data);
		return new ServletInputStream() {
			
			@Override
			public int read() throws IOException {
				// TODO Auto-generated method stub
				return configStream.read();
			}
			
			@Override
			public void setReadListener(ReadListener readListener) {}
			
			@Override
			public boolean isReady() {
				// TODO Auto-generated method stub
				return true;
			}
			
			@Override
			public boolean isFinished() {
				// TODO Auto-generated method stub
				return false;
			}
		};
	}
	
	protected void removeModuleConfig(final int applicationId, String ... configKeys) throws Throwable {
		setModuleConfig(applicationId, Arrays.asList(configKeys)
				.stream()
				.map(k -> new KeyValue<>(k, null))
				.toArray(KeyValue[]::new));
	}
	
	protected void setModuleConfig(final int applicationId, KeyValue<String> ... configuration) throws Throwable {
		AtomicInteger status = new AtomicInteger(200);
		final JemoUser adminUser = getTestAdminUser();
		getPluginManagerModule().process(new HttpServletRequestAdapter() {

			@Override
			public String getMethod() {
				return "POST";
			}

			@Override
			public String getContentType() {
				return "application/json";
			}

			@Override
			public String getParameter(String paramName) {
				if("ID".equals(paramName)) {
					return String.valueOf(applicationId);
				}
				return null;
			}
			
			@Override
			public String getRequestURI() {
				return "/jemo";
			}
			
			@Override
			public String getHeader(String headerName) {
				switch(headerName) {
				case "Authorization":
					return "Basic "+Base64.getEncoder().encodeToString((adminUser.getUsername()+":"+adminUser.getPassword()).getBytes(Util.UTF8_CHARSET));
				}
				
				return super.getHeader(headerName);
			}

			@Override
			public ServletInputStream getInputStream() throws IOException {
				ModuleConfiguration config = new ModuleConfiguration();
				if(configuration != null) {
					Arrays.asList(configuration).forEach(cfg -> {
						ModuleConfigurationParameter param = new ModuleConfigurationParameter();
						param.setKey(cfg.getKey());
						param.setValue(cfg.getValue());
						param.setOperation(cfg.getValue() == null ? ModuleConfigurationOperation.delete : ModuleConfigurationOperation.upsert);
						config.getParameters().add(param);
					});
				}
				return toInputStream(config);
			}
			
		}, new StatusResponseAdapter(status));
		if(status.get() != 200) {
			throw new Exception("Error the configuration could not be set correctly. The response code was: "+status.get());
		} else {
			//we should poll the configuration for this plugin until we see the new value appear
			long started = System.currentTimeMillis();
			do {
				Thread.sleep(TimeUnit.SECONDS.toMillis(1));
				if(jemoServer.getPluginManager().getModuleConfiguration(applicationId).entrySet()
					.stream().allMatch(e -> Arrays.asList(configuration)
							.stream()
							.anyMatch(cfg -> cfg.getKey().equals(e.getKey()) && cfg.getValue().equals(e.getValue())))) {
					break;
				}
			} while(TimeUnit.MILLISECONDS.toSeconds(System.currentTimeMillis() - started) < 10);
		}
	}
	
	protected String getAdminGroupId() throws Throwable {
		HttpServletRequestAdapter request = new HttpServletRequestAdapter() {
			@Override
			public String getMethod() {
				return "GET";
			}
			
			@Override
			public String getRequestURI() {
				return "/jemo/authentication/group";
			}
		};
		HttpServletResponseAdapter response = new HttpServletResponseAdapter() {};
		//we need an admin user to get the Admin group
		JemoAuthentication.processRequest(JemoAuthentication.getDefaultAdminUser(), request, response);
		JemoGroup[] groupList = (JemoGroup[]) Jemo.fromJSONString(Jemo.classOf(new JemoGroup[] {}), response.getResponseBody());
		Field groupAdminField = JemoAuthentication.class.getDeclaredField("GROUP_ADMIN");
		groupAdminField.setAccessible(true);
		for(JemoGroup group : groupList) {
			if(group.getId().equals(Util.md5((String)groupAdminField.get(JemoAuthentication.class)))) {
				return group.getId();
			}
		}
		
		return null;
	}
	
	private JemoUser TEST_ADMIN_USER = null;
	
	private static class StatusResponseAdapter extends HttpServletResponseAdapter {
		
		private final AtomicInteger status;
		
		public StatusResponseAdapter(AtomicInteger status) {
			this.status = status;
			this.status.set(200);
		}
		
		@Override
		public void setStatus(int i) {
			// TODO Auto-generated method stub
			status.set(i);
		}

		@Override
		public void setStatus(int i, String string) {
			// TODO Auto-generated method stub
			setStatus(i);
		}
	}
	
	private JemoUser getTestAdminUser() throws Throwable {
		if(TEST_ADMIN_USER == null) {
			TEST_ADMIN_USER = new JemoUser();
			TEST_ADMIN_USER.setAdmin(true);
			TEST_ADMIN_USER.setGroupIds(Arrays.asList(getAdminGroupId()));
			TEST_ADMIN_USER.setUsername(UUID.randomUUID().toString());
			TEST_ADMIN_USER.setPassword(UUID.randomUUID().toString());
			final AtomicInteger status = new AtomicInteger();
			JemoAuthentication.processRequest(JemoAuthentication.getDefaultAdminUser(), new HttpServletRequestAdapter() {
				@Override
				public String getRequestURI() {
					return "/jemo/authentication/user";
				}
				
				@Override
				public String getMethod() {
					return "POST";
				}
				
				
				@Override
				public ServletInputStream getInputStream() throws IOException {
					return toInputStream(TEST_ADMIN_USER);
				}
			}, new StatusResponseAdapter(status));
			if(status.get() != 200) {
				TEST_ADMIN_USER = null;
				throw new Exception("Could not create default TEST ADMIN user. The error was "+status.get());
			}
		}
		
		return TEST_ADMIN_USER;
	}
}
