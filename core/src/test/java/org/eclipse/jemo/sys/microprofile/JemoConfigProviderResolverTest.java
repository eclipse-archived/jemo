/*
********************************************************************************
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

package org.eclipse.jemo.sys.microprofile;

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.util.AbstractMap;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jemo.JemoGSMTest;
import org.eclipse.jemo.api.EventModule;
import org.eclipse.jemo.api.KeyValue;
import org.eclipse.jemo.internal.model.JemoMessage;
import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.junit.Test;

/**
 * The purpose of this test file is to ensure that the class org.eclipse.jemo.sys.microprofile.JemoConfigProviderResolver
 * obtains 100% code coverage from a unit test standpoint.
 * 
 * Because this class can only be used within an Active Jemo module we will need to instantiate the server to make sure these
 * tests run correctly.
 * 
 * @author Christopher Stura "cstura@gmail.com"
 */
public class JemoConfigProviderResolverTest extends JemoGSMTest {
	
	private static final ReentrantLock RUN_LOCK = new ReentrantLock();
	private static CountDownLatch PROCESS_LOCK = null;
	private static final String MSG_TYPE = "MESSAGE_TYPE";
	private static final String GET_CONFIG = "GET_CONFIG";
	private static final String GET_CONFIG_CLASSLOADER = "GET_CONFIG_CLS";
	private static final String GET_CONFIG_NULL = "GET_CONFIG_NULL";
	private static final String GET_CONFIG_NESTED = "GET_CONFIG_NESTED";
	private static final String GET_CONFIG_NULL_NO_PARENT = "GET_CONFIG_NULL_NO_PARENT";
	private static final String GET_CONFIG_BUILDER = "GET_CONFIG_BUILDER";
	private static final String REGISTER_CONFIG = "REGISTER_CONFIG";
	private static final String REGISTER_CONFIG_WHEN_NULL = "REGISTER_CONFIG_NULL";
	private static final String REGISTER_CONFIG_NULL = "REGISTER_NULL_CONFIG";
	private static final String REGISTER_CONFIG_AGAIN = "REGISTER_CONFIG_AGAIN";
	private static final String RELEASE_CONFIG = "RELEASE_CONFIG";
	private static final String RELEASE_CONFIG_NULL = "RELEASE_CONFIG_NULL";
	private static final String RELEASE_CONFIG_INVALID_CLASSLOADER = "RELEASE_CONFIG_INVALID_CLASSLOADER";
	private static final String RELEASE_CONFIG_UNMATCHED = "RELEASE_CONFIG_UNMATCHED";
	private static final AtomicBoolean IS_DEPLOYED = new AtomicBoolean(false);
	private static final int APP_ID = 92000;
	
	private static AtomicReference<Object> RESULT = new AtomicReference<>(null);
	
	
	public static class TestModule implements EventModule {

		@Override
		public JemoMessage process(JemoMessage message) throws Throwable {
			JemoConfigProviderResolver configResolver = new JemoConfigProviderResolver();
			
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
			Util.createJar(byteOut, TestModule.class);
			
			switch((String)message.getAttributes().get(MSG_TYPE)) {
			case GET_CONFIG:
				RESULT.set(configResolver.getConfig());
				break;
			case GET_CONFIG_CLASSLOADER:
				RESULT.set(configResolver.getConfig(new ClassLoader(Thread.currentThread().getContextClassLoader()) {}));
				break;
			case GET_CONFIG_NULL:
				RESULT.set(configResolver.getConfig(null));
				break;
			case GET_CONFIG_NESTED:
				RESULT.set(configResolver.getConfig(new JemoClassLoader(UUID.randomUUID().toString(), byteOut.toByteArray(), Thread.currentThread().getContextClassLoader())));
				break;
			case GET_CONFIG_NULL_NO_PARENT:
				RESULT.set(configResolver.getConfig(new ClassLoader(null) {}));
				break;
			case GET_CONFIG_BUILDER:
				RESULT.set(configResolver.getBuilder());
				break;
			case REGISTER_CONFIG:
				Config cfg = configResolver.getConfig();
				if(cfg.getOptionalValue("test", String.class).isPresent()) {
					configResolver.releaseConfig(cfg);
				}
				configResolver.registerConfig(new JemoConfig(Util.MAP(new AbstractMap.SimpleEntry<>("test", "value")), null), Thread.currentThread().getContextClassLoader());
				RESULT.set(configResolver.getConfig());
				break;
			case REGISTER_CONFIG_WHEN_NULL:
				try {
					ClassLoader clsLoader = new JemoClassLoader(UUID.randomUUID().toString(), byteOut.toByteArray(), null) {
	
						@Override
						public int getApplicationId() {
							return APP_ID;
						}
						
					};
					configResolver.registerConfig(new JemoConfig(Util.MAP(new AbstractMap.SimpleEntry<>("test2", "value2")), null), clsLoader);
					RESULT.set(configResolver.getConfig(clsLoader));
				}catch(IllegalStateException stateEx) {
					RESULT.set(stateEx);
				}
				break;
			case REGISTER_CONFIG_NULL:
				try {
					configResolver.registerConfig(new JemoConfig(Util.MAP(new AbstractMap.SimpleEntry<>("test2", "value2")), null), null);
				}catch(IllegalStateException stateEx) {
					RESULT.set(stateEx);
				}
				break;
			case REGISTER_CONFIG_AGAIN:
				try {
					cfg = configResolver.getConfig();
					if(!cfg.getOptionalValue("test", String.class).isPresent()) {
						//make sure the initial configuration is present
						configResolver.registerConfig(new JemoConfig(Util.MAP(new AbstractMap.SimpleEntry<>("test", "value")), null), Thread.currentThread().getContextClassLoader());
					}
					//now try and register another configuration
					configResolver.registerConfig(new JemoConfig(Util.MAP(new AbstractMap.SimpleEntry<>("test", "value")), null), Thread.currentThread().getContextClassLoader());
				}catch(IllegalStateException stateEx) {
					RESULT.set(stateEx);
				}
				break;
			case RELEASE_CONFIG:
				cfg = configResolver.getConfig();
				if(!cfg.getOptionalValue("test", String.class).isPresent()) {
					//make sure the initial configuration is present
					configResolver.registerConfig(new JemoConfig(Util.MAP(new AbstractMap.SimpleEntry<>("test", "value")), null), Thread.currentThread().getContextClassLoader());
				}
				cfg = configResolver.getConfig();
				configResolver.releaseConfig(cfg);
				RESULT.set(configResolver.getConfig());
				break;
			case RELEASE_CONFIG_NULL:
				cfg = configResolver.getConfig();
				if(!cfg.getOptionalValue("test", String.class).isPresent()) {
					//make sure the initial configuration is present
					configResolver.registerConfig(new JemoConfig(Util.MAP(new AbstractMap.SimpleEntry<>("test", "value")), null), Thread.currentThread().getContextClassLoader());
				}
				cfg = configResolver.getConfig();
				configResolver.releaseConfig(null);
				RESULT.set(configResolver.getConfig());
				break;
			case RELEASE_CONFIG_INVALID_CLASSLOADER:
				cfg = configResolver.getConfig();
				if(!cfg.getOptionalValue("test", String.class).isPresent()) {
					//make sure the initial configuration is present
					configResolver.registerConfig(new JemoConfig(Util.MAP(new AbstractMap.SimpleEntry<>("test", "value")), null), Thread.currentThread().getContextClassLoader());
				}
				cfg = configResolver.getConfig();
				ClassLoader currentClassLoader = Thread.currentThread().getContextClassLoader();
				try {
					Thread.currentThread().setContextClassLoader(new ClassLoader(null) {});
					configResolver.releaseConfig(cfg);
				}finally {
					Thread.currentThread().setContextClassLoader(currentClassLoader);
				}
				RESULT.set(configResolver.getConfig());
				break;
			case RELEASE_CONFIG_UNMATCHED:
				cfg = configResolver.getConfig();
				if(!cfg.getOptionalValue("test", String.class).isPresent()) {
					//make sure the initial configuration is present
					configResolver.registerConfig(new JemoConfig(Util.MAP(new AbstractMap.SimpleEntry<>("test", "value")), null), Thread.currentThread().getContextClassLoader());
				}
				configResolver.releaseConfig(new JemoConfig(Util.MAP(new AbstractMap.SimpleEntry<>("test2", "value2")), null));
				RESULT.set(configResolver.getConfig());
				break;
			}
			
			PROCESS_LOCK.countDown();
			return null;
		}
		
	}
	
	protected <T extends Object> T runThroughEvent(final String messageType,Class<T> returnType) throws Throwable {
		RUN_LOCK.lock();
		try {
			RESULT.set(null);
			if(IS_DEPLOYED.compareAndSet(false, true)) {
				uploadPlugin(APP_ID, 1.0, "TestModule", TestModule.class);
			}
			PROCESS_LOCK = new CountDownLatch(1);
			sendMessage(APP_ID, 1.0, TestModule.class, JemoMessage.LOCATION_ANYWHERE, 
					new KeyValue<>(MSG_TYPE, messageType));
			if(PROCESS_LOCK.await(30, TimeUnit.SECONDS)) {
				T ref = returnType.cast(RESULT.get());
				return ref;
			}
			
			return null;
		}finally {
			RUN_LOCK.unlock();
		}
	}
	
	@Test
	public void testGetConfig() throws Throwable {
		Config appConfig = runThroughEvent(GET_CONFIG, Config.class);
		assertNotNull(appConfig);
		appConfig = runThroughEvent(GET_CONFIG_CLASSLOADER, Config.class);
		assertNotNull(appConfig);
		appConfig = runThroughEvent(GET_CONFIG_NULL, Config.class);
		assertNull(appConfig);
		appConfig = runThroughEvent(GET_CONFIG_NESTED, Config.class);
		assertNotNull(appConfig);
		appConfig = runThroughEvent(GET_CONFIG_NULL_NO_PARENT, Config.class);
		assertNull(appConfig);
	}
	
	@Test
	public void testGetBuilder() throws Throwable {
		ConfigBuilder builder = runThroughEvent(GET_CONFIG_BUILDER, ConfigBuilder.class);
		assertNotNull(builder);
	}
	
	@Test
	public void testRegisterConfig() throws Throwable {
		Config appConfig = runThroughEvent(REGISTER_CONFIG, Config.class);
		assertNotNull(appConfig);
		assertTrue(appConfig.getOptionalValue("test", String.class).isPresent());
		assertEquals("value", appConfig.getValue("test", String.class));
		IllegalStateException ex = runThroughEvent(REGISTER_CONFIG_WHEN_NULL, IllegalStateException.class);
		assertNotNull(ex);
		ex = runThroughEvent(REGISTER_CONFIG_NULL, IllegalStateException.class);
		assertNotNull(ex);
		ex = runThroughEvent(REGISTER_CONFIG_AGAIN, IllegalStateException.class);
		assertNotNull(ex);
	}
	
	@Test
	public void testReleaseConfig() throws Throwable {
		Config appConfig = runThroughEvent(RELEASE_CONFIG, Config.class);
		assertNotNull(appConfig);
		assertFalse(appConfig.getOptionalValue("test", String.class).isPresent());
		appConfig = runThroughEvent(RELEASE_CONFIG_NULL, Config.class);
		assertNotNull(appConfig);
		assertTrue(appConfig.getOptionalValue("test", String.class).isPresent());
		assertEquals("value", appConfig.getValue("test", String.class));
		appConfig = runThroughEvent(RELEASE_CONFIG_INVALID_CLASSLOADER, Config.class);
		assertNotNull(appConfig);
		assertTrue(appConfig.getOptionalValue("test", String.class).isPresent());
		assertEquals("value", appConfig.getValue("test", String.class));
		appConfig = runThroughEvent(RELEASE_CONFIG_UNMATCHED, Config.class);
		assertNotNull(appConfig);
		assertTrue(appConfig.getOptionalValue("test", String.class).isPresent());
		assertEquals("value", appConfig.getValue("test", String.class));
	}
}
