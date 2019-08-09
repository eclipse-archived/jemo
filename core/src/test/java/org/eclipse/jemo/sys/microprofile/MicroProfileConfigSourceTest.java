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

import java.util.Properties;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jemo.JemoGSMTest;
import org.eclipse.jemo.api.FixedModule;
import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.JemoPluginManager;
import org.eclipse.jemo.sys.internal.JarEntry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MicroProfileConfigSourceTest extends JemoGSMTest {
	
	static AtomicReference<MicroProfileConfigSource> config = new AtomicReference<>();
	static CountDownLatch END_MODULE_LATCH = null;
	
	/**
	 * initial setup, we need a Jemo module which implements the processFixed handler.
	 */
	public static class FixedTestModule implements FixedModule {

		final CountDownLatch WAIT_TO_EXIT = new CountDownLatch(1);
		
		@Override
		public void processFixed(String location, String instanceId) throws Throwable {
			config.set(new MicroProfileConfigSource((JemoClassLoader)Thread.currentThread().getContextClassLoader()));
			END_MODULE_LATCH.countDown();
			//if this terminates it will be re-launched as the failure will be detected and hence skew the results of the test.
			WAIT_TO_EXIT.await();
		}

		@Override
		public void stop() {
			WAIT_TO_EXIT.countDown();
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
	
	/**
	 * this test will create a module with a META-INF/microprofile-config.properties file inside of
	 * it, build a jar with both the module file and this resource file.
	 * 
	 * The module inside of it's processFixed method will create an instance of the MicroProfileConfigSource
	 * and we will expect to be able to access the properties in the file through the created source
	 * so we can validate them.
	 */
	@Test
	public void testConstructor() throws Throwable {
		final Properties appConfig = new Properties();
		appConfig.setProperty("test", "value");
		
		//handle branch 1 (a case when the microprofile configuration file exists)
		END_MODULE_LATCH = new CountDownLatch(1);
		uploadPlugin(91000, 1.0, FixedTestModule.class.getSimpleName(), 
				JarEntry.fromClass(FixedTestModule.class), 
				JarEntry.fromProperties(MicroProfileConfigSource.MICROPROFILE_CONFIG, appConfig));
		
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		//a fixed module will start immediately but there will be a bit of a lag because it will start asynchronously. we will use a CountDownLatch to wait 
		//for a maximum period of time before checking the result.
		END_MODULE_LATCH.await(30, TimeUnit.SECONDS);
		
		//now verify that the configuration contains the property value that was set.
		assertTrue(config.get().getProperties().containsKey("test"));
		assertEquals("value", config.get().getValue("test"));
		
		//handle branch 2 (a case when the configuration file does not exist)
		END_MODULE_LATCH = new CountDownLatch(1);
		uploadPlugin(91001,1.0,FixedTestModule.class.getSimpleName(), FixedTestModule.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		END_MODULE_LATCH.await(30, TimeUnit.SECONDS);
		assertFalse(config.get().getProperties().containsKey("test"));
	}
	
	@Test
	public void testGetName() throws Throwable {
		END_MODULE_LATCH = new CountDownLatch(1);
		uploadPlugin(91002,1.0,FixedTestModule.class.getSimpleName(), FixedTestModule.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		END_MODULE_LATCH.await(30, TimeUnit.SECONDS);
		assertEquals(MicroProfileConfigSource.MICROPROFILE_CONFIG, config.get().getName());
	}
	
	@Test
	public void testGetOrdinal() throws Throwable {
		END_MODULE_LATCH = new CountDownLatch(1);
		uploadPlugin(91003,1.0,FixedTestModule.class.getSimpleName(), FixedTestModule.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		END_MODULE_LATCH.await(30, TimeUnit.SECONDS);
		assertEquals(200, config.get().getOrdinal());
	}
}
