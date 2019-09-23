/*
********************************************************************************
* Copyright (c) 2nd August 2019
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
package org.eclipse.jemo.sys;

import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.JemoGSMTest;
import org.eclipse.jemo.api.KeyValue;
import org.eclipse.jemo.api.Module;
import org.eclipse.jemo.internal.model.JemoMessage;
import org.eclipse.jemo.internal.model.CloudProvider;
import org.eclipse.jemo.internal.model.CloudRuntime;
import org.eclipse.jemo.internal.model.SystemDBObject;
import org.eclipse.jemo.internal.model.ValidationResult;
import org.eclipse.jemo.internal.model.JemoApplicationMetaData;
import org.eclipse.jemo.runtime.MemoryRuntime;
import org.eclipse.jemo.sys.JemoPluginManager.ModuleInfoCache;
import org.eclipse.jemo.sys.auth.JemoAuthentication;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.HttpServletRequestAdapter;
import org.eclipse.jemo.HttpServletResponseAdapter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.Holder;
import static org.junit.Assert.*;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestPluginManager extends JemoGSMTest {
	
	public TestPluginManager() throws Throwable { super(); }
	
	public static class InstallModeJemo extends AbstractJemo {
		public InstallModeJemo() {
			super("INSTALL", 9000, 9001, null, null, 10, false, false, null, Level.OFF, UUID.randomUUID().toString());
		}

		@Override
		public boolean isInInstallationMode() {
			// TODO Auto-generated method stub
			return true;
		}
	}
	
	Lock sequential = new ReentrantLock();

	@Before
	public void setUp() throws Exception {
	    sequential.lock();
	}

	@After
	public void tearDown() throws Exception {
	    sequential.unlock();
	}
	
	@Test
	public void testConstructor() throws Throwable {
		//1. we need to test the creation of a plugin manager in installation mode.
		InstallModeJemo jemoInst = new InstallModeJemo();
		//before we startup jemo we need to make sure our invalid permissions runtime is being used.
		JemoPluginManager pluginManager = new JemoPluginManager(jemoInst);
		assertTrue(pluginManager.getModuleList(jemoInst.getINSTANCE_ID()).length == 0);
	}
	
	@Test
	public void testGetInstances() throws Throwable {
		Class pluginManagerClass = Class.forName("org.eclipse.jemo.sys.JemoPluginManager");
		Object pluginManager = pluginManagerClass.getMethod("getInstance").invoke(pluginManagerClass);
		List<String> instances = (List)pluginManagerClass.getMethod("listInstances", String.class).invoke(pluginManager, "AWS");
		Logger.getAnonymousLogger().info(String.format("instances: %s", instances));
		CloudRuntime runtime = CloudProvider.getInstance().getRuntime();
		
		JemoPluginManager jemoPluginManager = (JemoPluginManager)pluginManager;
		jemoPluginManager.runWithModuleContext(Void.class, x -> {
			Map<String,String> payload = new HashMap<>();
			JemoMessage msg = new JemoMessage();
			msg.setSourcePluginId(20);
			msg.setPluginId(20);
			msg.setModuleClass("org.eclipse.jemo.connect.ConnectModule");
			/*msg.getAttributes().put("queue", getName()); //name of connect queue
			msg.getAttributes().put("class", m.getClass().getName()); //the connect class which recieves the queue (EventProcessor implementation)
			msg.getAttributes().put("pluginId", pluginId); //the id of the connect plugin.*/

			instances.forEach(i -> {
				try {
					Long whenWasILaunched = runtime.retrieve("metoffice_instance_run_"+i, Long.class);
					if(whenWasILaunched == null || System.currentTimeMillis()-whenWasILaunched > TimeUnit.HOURS.toMillis(2)) {
						runtime.store("metoffice_instance_run_"+i, System.currentTimeMillis());
						msg.getAttributes().put("payload", Jemo.toJSONString(payload)); //the data to send to connect.
						msg.send(runtime.getQueueId("JEMO-AWS-"+i));
					}
				}catch(Throwable ex) {
					throw new RuntimeException(ex);
				}
			});
			
			return null;
		});
	}
	
	@Test
	public void test_getLocationList() throws Throwable {
		//so for this test to pass we must have a queue id value for all of the locations in the list.
		Set<String> locations = jemoServer.getPluginManager().getLocationList();
		Set<String> queueIdList = locations.stream()
			.map(l -> CloudProvider.getInstance().getRuntime().getQueueId("JEMO-"+l+"-WORK-QUEUE"))
			.filter(qId -> qId != null)
			.collect(Collectors.toSet());
		
		assertEquals(locations.size(),queueIdList.size());
	}
	
	@Test
	public void test_getActiveLocationList() throws Throwable {
		Set<String> activeLocations = jemoServer.getPluginManager().getActiveLocationList();
		Set<String> queueIdList = activeLocations.stream()
			.map(l -> CloudProvider.getInstance().getRuntime().getQueueId("JEMO-"+l+"-WORK-QUEUE"))
			.filter(qId -> qId != null)
			.collect(Collectors.toSet());
		assertEquals(activeLocations.size(),queueIdList.size());
		
		//also each location must have at least 1 active instance.
		Set<String> activeInstances = activeLocations.stream()
			.filter(l -> !jemoServer.getPluginManager().listInstances(l).isEmpty())
			.collect(Collectors.toSet());
		assertEquals(activeLocations.size(),activeInstances.size());
	}
	
	@Test
	public void test_getInstanceLocations() throws Throwable {
		Set<String> instanceList = jemoServer.getPluginManager().getActiveInstanceList();
		assertArrayEquals(jemoServer.getPluginManager().getActiveLocationList().toArray(), jemoServer.getPluginManager().getInstanceLocations(instanceList.toArray(new String[] {})).toArray());
	}
	
	@Test
	public void test_getInstanceLocationMap() throws Throwable {
		assertEquals(locationList().stream()
			.flatMap(l -> instanceList(l).stream().map(i -> new KeyValue<>(i,l)))
			.collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue)), jemoServer.getPluginManager().getInstanceLocationMap(jemoServer.getPluginManager().getActiveInstanceList().toArray(new String[] {})));
	}
	
	@Test
	public void test_MonitoringInterval_httpRequest() {
		JemoPluginManager.MonitoringInterval interval = new JemoPluginManager.MonitoringInterval("1M", 1, TimeUnit.MINUTES);
		interval.httpRequest(1000);
		assertEquals(1,interval.getHttpRequests());
		assertEquals(1000,interval.getTotalHttpTime());
	}
	
	@Test
	public void test_MonitoringInterval_checkInterval() {
		JemoPluginManager.MonitoringInterval interval = new JemoPluginManager.MonitoringInterval("1M", 1, TimeUnit.MINUTES);
		interval.httpRequest(1000);
		assertEquals(1,interval.getHttpRequests());
		assertEquals(1000,interval.getTotalHttpTime());
		interval.eventRequest(100);
		assertEquals(1,interval.getEventRequests());
		assertEquals(100,interval.getTotalEventTime());
		assertEquals("1M",interval.getKey());
		assertEquals(TimeUnit.MINUTES.toMillis(1), interval.getDuration());
		Util.setFieldValue(interval, "intervalStart", System.currentTimeMillis() - (interval.getDuration()+100));
		interval.eventRequest(100);
		assertEquals(1,interval.getEventRequests());
		assertEquals(100,interval.getTotalEventTime());
	}
	
	@Test
	public void test_ModuleInfoCache() {
		JemoPluginManager.ModuleInfoCache cache = new JemoPluginManager.ModuleInfoCache("TEST1",new HashSet<>());
		assertEquals("TEST1",cache.getLocation());
		long cachedOn = System.currentTimeMillis();
		Util.setFieldValue(cache, "cachedOn", cachedOn);
		assertEquals(cachedOn, cache.getCachedOn());
	}
	
	@Test
	public void test_listApplications() throws Throwable {
		//we need to set a mock cloud runtime up first
		Map<String, JemoApplicationMetaData> origKnownApplications = new HashMap<>();
		Map<String, JemoApplicationMetaData> knownApplications = Util.getFieldValue(jemoServer.getPluginManager(),"KNOWN_APPLICATIONS",Map.class);
		try {
			origKnownApplications.putAll(knownApplications);
			knownApplications.clear();
			CloudProvider.defineCustomeRuntime(new MemoryRuntime() {
				List<SystemDBObject> objList = new ArrayList<>();
				
				@Override
				public <T> List<T> listNoSQL(String tableName, Class<T> objectType) {
					if(tableName.contentEquals(JemoPluginManager.MODULE_METADATA_TABLE)) {
						JemoApplicationMetaData app = new JemoApplicationMetaData();
						app.setId("11_Test-1-1.0.jar");
						app.setEnabled(false);
						return Arrays.asList(objectType.cast(app));
					} else {
						return objList.stream().map(obj -> objectType.cast(obj)).collect(Collectors.toList());
					}
				}

				@Override
				public Set<String> listPlugins() {
					return Arrays.asList("10_Test-1-1.0.jar", "11_Test-1-1.0.jar").stream().collect(Collectors.toSet());
				}

				@Override
				public boolean hasNoSQLTable(String tableName) {
					return true;
				}

				@Override
				public Long getModuleInstallDate(String moduleJar) throws IOException {
					return System.currentTimeMillis();
				}

				@Override
				public void saveNoSQL(String tableName, SystemDBObject... data) {
					objList.addAll(Arrays.asList(data));
				}
			});
			jemoServer.getPluginManager().listApplications();
			assertEquals(1,knownApplications.size());
			JemoApplicationMetaData resultMetadata = knownApplications.entrySet().iterator().next().getValue();
			assertNotNull(resultMetadata);
			JemoApplicationMetaData targetMetadata = new JemoApplicationMetaData();
			targetMetadata.setId("10_Test-1-1.0.jar");
		}finally {
			CloudProvider.defineCustomeRuntime(null);
			knownApplications.clear();
			knownApplications.putAll(origKnownApplications);
		}
		
		//upload a test module
		uploadPlugin(101, 1.0, "TestApp", new ByteArrayInputStream(buildTestApplicationJar()));
		JemoApplicationMetaData metaData = jemoServer.getPluginManager().getApplication(101, 1.0);
		assertNotNull(metaData);
		metaData.setEnabled(false);
		getPluginManagerModule().changeState(metaData, null);
		//a list of the applications should still return this app
		assertTrue(jemoServer.getPluginManager().listApplications().stream().anyMatch(app -> app.getId().equals(metaData.getId())));
		//the application should also be disabled.
		assertFalse(jemoServer.getPluginManager().listApplications().stream().anyMatch(app -> app.getId().equals(metaData.getId()) && app.isEnabled()));
	}
	
	@Test
	public void test_getVirtualHostMap() {
		try {
			CloudProvider.defineCustomeRuntime(new MemoryRuntime() {
				@Override
				public <T> T retrieve(String key, Class<T> objType) {
					if(key.equals(JemoPluginManager.VHOST_KEY)) {
						Map<String,String> currentDefinitions = new HashMap<>();
						currentDefinitions.put("www.google.com","/3/v1.0/google");
						return objType.cast(currentDefinitions);
					}
					
					return super.retrieve(key, objType);
				}
			});
			assertNotNull(jemoServer.getPluginManager().getVirtualHostMap());
			jemoServer.getPluginManager().loadVirtualHostDefinitions();
			assertFalse(jemoServer.getPluginManager().getVirtualHostMap().isEmpty());
			assertTrue(jemoServer.getPluginManager().getVirtualHostMap().containsKey("www.google.com"));
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
	}
	
	@Test
	public void test_getMonitoringInterval() {
		assertNotNull(jemoServer.getPluginManager().getMonitoringInterval("5M"));
	}
	
	@Test
	public void test_listMonitoringIntervals() {
		jemoServer.getPluginManager().listMonitoringIntervals().forEach(interval -> assertNotNull(jemoServer.getPluginManager().getMonitoringInterval(interval)));
	}
	
	@Test
	public void test_PLUGIN_INSTALLED_ON() {
		try {
			CloudProvider.defineCustomeRuntime(new MemoryRuntime() {
				@Override
				public Long getModuleInstallDate(String moduleJar) throws IOException {
					throw new IOException(moduleJar);
				}
			});
			long now = System.currentTimeMillis();
			long installedOn = JemoPluginManager.PLUGIN_INSTALLED_ON("10_test-1-1.0.jar");
			assertTrue(installedOn >= now);
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
	}
	
	@Test
	public void test_MEMORY_CHECK() {
		assertTrue(JemoPluginManager.MEMORY_CHECK() > 0);
		assertTrue(JemoPluginManager.garbageCollectIfNecessary(0));
		assertFalse(JemoPluginManager.garbageCollectIfNecessary(150000));
	}
	
	public static class TestModule implements Module {

		@Override
		public void construct(Logger logger, String name, int id, double version) {}
		
	}
	
	public static abstract class TestAbstractModule implements Module {
		@Override
		public void construct(Logger logger, String name, int id, double version) {}
	}
	
	public static class TestInstantiationErrorModule implements Module {
		static {
			Util.B(null, x -> { throw new RuntimeException(); }); 
		}
		
		@Override
		public void construct(Logger logger, String name, int id, double version) {}
	}
	
	private byte[] buildTestApplicationJar() throws Throwable {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		Util.createJar(byteOut, TestModule.class, TestPluginManager.class, TestAbstractModule.class, 
								TestInstantiationErrorModule.class, TestWebModule.class,
								TestWebModuleTimeout.class);
		return byteOut.toByteArray();
	}
	
	@Test
	public void test_MODULE_LIST() throws Throwable {
		byte[] jarBytes = buildTestApplicationJar();
		try(JemoClassLoader clsLoader = new JemoClassLoader(UUID.randomUUID().toString(), jarBytes)) {
			List<String> moduleList = jemoServer.getPluginManager().MODULE_LIST("10_Test-1-1.0.jar", jarBytes, clsLoader);
			assertTrue(moduleList.contains(TestModule.class.getName()));
		}
		assertTrue(jemoServer.getPluginManager().MODULE_LIST("100_Test-1-1.0.jar", null, null).isEmpty());
		CloudProvider.getInstance().getRuntime().uploadModule("101_Test-1-1.0.jar", jarBytes);
		List<String> moduleList = jemoServer.getPluginManager().MODULE_LIST("101_Test-1-1.0.jar");
		assertTrue(moduleList.contains(TestModule.class.getName()));
		try {
			CloudProvider.defineCustomeRuntime(new MemoryRuntime() {});
			assertTrue(jemoServer.getPluginManager().MODULE_LIST("102_Test-1-1.0.jar").isEmpty());
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
		//lets now run a test where the get module list runtime method will return an empty list instead of null.
		try {
			CloudProvider.defineCustomeRuntime(new MemoryRuntime() {
				@Override
				public List<String> getModuleList(String jarFileName) {
					return new ArrayList<>();
				}
			});
			assertTrue(jemoServer.getPluginManager().MODULE_LIST("100_Test-1-1.0.jar", jarBytes, null).isEmpty());
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
		//now we need to test a case where an error is thrown by the runtime. we expect an empty list to be returned.
		try {
			CloudProvider.defineCustomeRuntime(new MemoryRuntime() {
				@Override
				public List<String> getModuleList(String jarFileName) {
					throw new RuntimeException();
				}
			});
			assertTrue(jemoServer.getPluginManager().MODULE_LIST("100_Test-1-1.0.jar", jarBytes, null).isEmpty());
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
		assertTrue(jemoServer.getPluginManager()
				.MODULE_LIST(Arrays.asList("org.test.ClassDoesNotExist"), new JemoClassLoader(UUID.randomUUID().toString(), jarBytes)).isEmpty());
	}
	
	@Test
	public void test_listPlugins() {
		List<JemoApplicationMetaData> appList = Util.getFieldValue(jemoServer.getPluginManager(), "APPLICATION_LIST", List.class);
		assertEquals("APPLIST = "+appList.stream().map(app -> app.getId()).collect(Collectors.joining(","))+
				" PLUGIN_ID = "+jemoServer.getPluginManager().listPluginIds().stream().map(id -> String.valueOf(id))
				.collect(Collectors.joining(",")),
				appList.stream().map(app -> JemoPluginManager.PLUGIN_ID(app.getId())).distinct().count(), jemoServer.getPluginManager().listPluginIds().size());
	}
	
	public static class TestWebModule implements Module {

		@Override
		public void construct(Logger logger, String name, int id, double version) {}
		
		@Override
		public String getBasePath() {
			return "/test";
		}

		@Override
		public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {}
		
	}
	
	public static class TestWebModuleTimeout implements Module {

		@Override
		public void construct(Logger logger, String name, int id, double version) {}
		
		@Override
		public String getBasePath() {
			return "/test_timeout";
		}

		@Override
		public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
			Thread.sleep(TimeUnit.SECONDS.toMillis(21));
		}
		
	}
	
	@Test
	public void test_process() throws Throwable {
		try {
			JemoMessage msg = new JemoMessage();
			msg.setPluginId(100);
			msg.setModuleClass("org.test.com.Test");
			jemoServer.getPluginManager().process(msg);
		}catch(Throwable rtEx) {
			assertEquals("Module: 100 - org.test.com.Test could not be found", rtEx.getMessage());
		}
		
		Holder<String> errorStr = new Holder<>();
		jemoServer.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "/1/v1.0/test";
			}

			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://localhost:8080/1/v1.0/test");
			}

		}, new HttpServletResponseAdapter() {
			@Override
			public void sendError(int i, String string) throws IOException {
				errorStr.value = string;
			}
		});
		assertEquals("no module mapping defined for: /1/v1.0/test supported mappings are: "+Util.getFieldValue(jemoServer.getPluginManager(), "moduleEndpointMap", Map.class).toString(), errorStr.value);
		//ok now lets test the virtual host scenario
		Map<String,String> vhostMap = Util.getFieldValue(jemoServer.getPluginManager(), "virtualHostMap", Map.class);
		vhostMap.put("//www.google.com", "/1/v1.0/test");
		jemoServer.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "";
			}

			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://www.google.com/");
			}

		}, new HttpServletResponseAdapter() {
			@Override
			public void sendError(int i, String string) throws IOException {
				errorStr.value = string;
			}
		});
		assertEquals("the path: /1/v1.0/test does not currespond to any mappings. supported mappings are: "+Util.getFieldValue(jemoServer.getPluginManager(), "moduleEndpointMap", Map.class).toString(), errorStr.value);
		//ok now lets add a module map for our test module.
		uploadPlugin(1, 1.0, TestWebModule.class.getSimpleName(), TestWebModule.class, TestPluginManager.class);
		//we now need to wait until the upload has completed.
		do {
			TimeUnit.SECONDS.sleep(1);
		}while(!((List<JemoApplicationMetaData>)Util.getFieldValue(jemoServer.getPluginManager(),"APPLICATION_LIST", List.class)).stream().anyMatch(app -> JemoPluginManager.PLUGIN_ID(app.getId()) == 1));
		assertNotNull(Util.getFieldValue(jemoServer.getPluginManager(), "moduleEndpointMap", Map.class).get("/1/v1.0/test"));
		errorStr.value = null;
		jemoServer.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "";
			}

			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://www.google.com/");
			}

		}, new HttpServletResponseAdapter() {
			@Override
			public void sendError(int i, String string) throws IOException {
				errorStr.value = string;
			}
		});
		assertNull(errorStr.value);

		//we need to call a url on the plugin manager
		jemoServer.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "/jemo/check";
			}

			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://localhost:8080/jemo/check");
			}

			@Override
			public String getRequestURI() {
				return "https://localhost:8080/jemo/check";
			}

			@Override
			public String getHeader(String string) {
				return null;
			}

			@Override
			public String getMethod() {
				return "GET";
			}
		}, new HttpServletResponseAdapter() {
			@Override
			public void sendError(int i, String string) throws IOException {
				errorStr.value = string;
			}
		});
		assertNull(errorStr.value);
	}
	
	@Test
	public void test_process_http_pluginmanager_module() throws Throwable {
		Holder<String> contentType = new Holder<>();
		jemoServer.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "/jemo/check";
			}

			@Override
			public String getRequestURI() {
				return getServletPath();
			}
			
			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://localhost:8080/jemo/check");
			}

			@Override
			public String getHeader(String string) {
				return "Basic "+Base64.getEncoder().encodeToString("test:test".getBytes(Util.UTF8_CHARSET));
			}

			@Override
			public String getParameter(String string) {
				return null;
			}

			@Override
			public String getMethod() {
				return "GET";
			}
			
			
			
		}, new HttpServletResponseAdapter() {
			@Override
			public void setContentType(String string) {
				contentType.value = string;
			}

			@Override
			public void setContentLength(int i) {
				
			}

			@Override
			public ServletOutputStream getOutputStream() throws IOException {
				return new ServletOutputStream() {
					private ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
					
					@Override
					public boolean isReady() {
						return true;
					}

					@Override
					public void setWriteListener(WriteListener wl) {}

					@Override
					public void write(int b) throws IOException {
						byteOut.write(b);
					}
				};
			}
			
			
			@Override
			public void sendError(int i, String string) throws IOException {}
		});
		assertNotNull(contentType.value);
		assertEquals("text/html",contentType.value);
		
		uploadPlugin(102, 1.0, "TestWebAppTimeout", new ByteArrayInputStream(buildTestApplicationJar()));
		AtomicReference<String> errorValue = new AtomicReference<>(null);
		HttpServletRequest request = new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "/102/v1.0/test_timeout";
			}

			@Override
			public String getRequestURI() {
				return getServletPath();
			}
			
			@Override
			public StringBuffer getRequestURL() {
				return new StringBuffer("https://localhost:8080"+getServletPath());
			}

			@Override
			public String getHeader(String string) {
				return "Basic "+Base64.getEncoder().encodeToString("test:test".getBytes(Util.UTF8_CHARSET));
			}

			@Override
			public String getParameter(String string) {
				return null;
			}

			@Override
			public String getMethod() {
				return "GET";
			}
			
			
			
		};
		HttpServletResponse response = new HttpServletResponseAdapter() {
			@Override
			public void setContentType(String string) {
				contentType.value = string;
			}

			@Override
			public void setContentLength(int i) {
				
			}

			@Override
			public ServletOutputStream getOutputStream() throws IOException {
				return new ServletOutputStream() {
					private ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
					
					@Override
					public boolean isReady() {
						return true;
					}

					@Override
					public void setWriteListener(WriteListener wl) {}

					@Override
					public void write(int b) throws IOException {
						byteOut.write(b);
					}
				};
			}
			
			
			@Override
			public void sendError(int i, String string) throws IOException {
				errorValue.set(string);
			}
		};
		jemoServer.getPluginManager().process(request, response);
		assertNotNull(errorValue.get());
		assertTrue(errorValue.get().contains("TimeoutException"));
		assertEquals(Integer.valueOf(1), Util.getFieldValue(jemoServer.getPluginManager(), "TIMEOUT_COUNT", Integer.class));
		//set the maximum timeouts to 1
		Util.setFieldValue(jemoServer.getPluginManager(), "MAX_TIMEOUT_COUNT", 1);
		jemoServer.getPluginManager().process(request, response);
		assertEquals(Integer.valueOf(0), Util.getFieldValue(jemoServer.getPluginManager(), "TIMEOUT_COUNT", Integer.class));
		//now lets set the memory threshold to something higher than the total amout of free memory and we should get a System.exit(0) call
		try {
			Util.setFieldValue(JemoPluginManager.class, "MEMORY_THRESHOLD", Long.MAX_VALUE);
			AtomicInteger exitValue = new AtomicInteger(0);
			final Consumer<Integer> EXIT_SYSTEM = (exitCode) -> exitValue.set(exitCode);
			Util.setFieldValue(Util.class, "EXIT_SYSTEM", EXIT_SYSTEM); 
			jemoServer.getPluginManager().process(request, response);
			assertEquals(0, exitValue.get());
			Util.setFieldValue(jemoServer.getPluginManager(), "MAX_TIMEOUT_COUNT", 10);
			jemoServer.getPluginManager().process(request, response);
			assertEquals(Integer.valueOf(2), Util.getFieldValue(jemoServer.getPluginManager(), "TIMEOUT_COUNT", Integer.class));
		}finally {
			Util.setFieldValue(JemoPluginManager.class, "MEMORY_THRESHOLD", 100000l);
		}
	}
	
	/**
	 * this scope of this test is to ensure that the getLiveModuleList method behaves as expected
	 * and that the existance of this test will avoid any regressions in the method.
	 * 
	 * @throws Throwable if an unexpected error occurs.
	 */
	@Test
	public void test_getLiveModuleList() throws Throwable {
		//1. test that the default application is present in all locations.
		Map<String, ModuleInfoCache> LIVE_MODULE_CACHE = Util.getFieldValue(jemoServer.getPluginManager(), "LIVE_MODULE_CACHE", Map.class);
		jemoServer.getPluginManager().getActiveLocationList().forEach(l -> {
			assertTrue(jemoServer.getPluginManager().getLiveModuleList(l)
				.stream()
				.anyMatch(m -> m.getId() == 0));
			//verify again as we will get a different path after cache but want the same result
			assertTrue(jemoServer.getPluginManager().getLiveModuleList(l)
					.stream()
					.anyMatch(m -> m.getId() == 0));
			//3. we now need to accelerate the expiration of the cache and validate the execution path.
			ModuleInfoCache modInfo = LIVE_MODULE_CACHE.get(l); //we expect the cache to be present.
			assertNotNull(modInfo);
			//set the cache date to 6 minutes in the past, which will force it to be flagged as expired
			Util.setFieldValue(modInfo, "cachedOn", System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(6));
			//verify again, the cache will be rebuilt so we should have the same result.
			assertTrue(jemoServer.getPluginManager().getLiveModuleList(l)
					.stream()
					.anyMatch(m -> m.getId() == 0));
		});
		//2. if we ask for the list from an invalid location we are expecting an empty list
		assertTrue(jemoServer.getPluginManager().getLiveModuleList("INVALID").isEmpty());
	}
	
	/**
	 * this test will validate whether all of the aspects of plugin removal work correctly within the PluginManager.
	 * 
	 * @throws Throwable
	 */
	@Test
	public void test_deletePlugin() throws Throwable {
		//1. we need to upload a fake plugin with a fake module in it.
		uploadPlugin(101, 1.0, "TestApp", new ByteArrayInputStream(buildTestApplicationJar()));
		//2. let's validate that this plugin actually has made it into the system.
		assertFalse(jemoServer.getPluginManager().loadModules("101_TestApp-1.0.jar").isEmpty());
		//3. we should remove this plugin
		getPluginManagerModule().deletePlugin(101, 1.0, null);
		assertNull(jemoServer.getPluginManager().loadModules("101_TestApp-1.0.jar"));
		//4. let's try and delete the same plugin again but with a defined user
		getPluginManagerModule().deletePlugin(101, 1.0, JemoAuthentication.getDefaultAdminUser());
		assertNull(jemoServer.getPluginManager().loadModules("101_TestApp-1.0.jar"));
		//5. upload the app again and try and delete it in a version that does not exist
		uploadPlugin(101, 1.0, "TestApp", new ByteArrayInputStream(buildTestApplicationJar()));
		assertFalse(jemoServer.getPluginManager().loadModules("101_TestApp-1.0.jar").isEmpty());
		getPluginManagerModule().deletePlugin(101, 2.0, null);
		assertFalse(jemoServer.getPluginManager().loadModules("101_TestApp-1.0.jar").isEmpty());
		getPluginManagerModule().deletePlugin(100, 2.0, null);
		assertFalse(jemoServer.getPluginManager().loadModules("101_TestApp-1.0.jar").isEmpty());
	}
	
	@Test
	public void test_cacheStreamToFile() throws Throwable {
		File f = JemoPluginManager.cacheStreamToFile(new ByteArrayInputStream("hello".getBytes(Util.UTF8_CHARSET)));
		assertEquals("hello", Util.toString(new FileInputStream(f)));
		f.delete();
	}
	
	@Test
	public void test_buildPluginClassLoader() throws Throwable {
		final String pluginJar = "101_TestClassLoader-1.0.jar";
		CloudProvider.getInstance().getRuntime().store(pluginJar + ".crc32", Long.valueOf(0));
		try {
			assertNull(jemoServer.getPluginManager().buildPluginClassLoader(pluginJar));
		}finally {
			CloudProvider.getInstance().getRuntime().delete(CloudProvider.getInstance().getRuntime().getDefaultCategory(), pluginJar + ".crc32");
		}
	}
	
	@Test
	public void test_getServerInstance() {
		AbstractJemo defaultJemo = Util.getFieldValue(AbstractJemo.class, "DEFAULT_INSTANCE", AbstractJemo.class);
		try {
			Util.setFieldValue(AbstractJemo.class, "DEFAULT_INSTANCE", null);
			assertEquals(Jemo.SERVER_INSTANCE.getINSTANCE_ID(), JemoPluginManager.getServerInstance().getINSTANCE_ID());
		}finally {
			Util.setFieldValue(AbstractJemo.class, "DEFAULT_INSTANCE", defaultJemo);
		}
	}
	
	public static class EventModuleWithTimeoutException implements Module {

		@Override
		public JemoMessage process(JemoMessage message) throws Throwable {
			throw new TimeoutException();
		}
		
	}
	
	@Test
	public void test_runWithModule() throws Throwable {
		
	}
}
