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
import org.eclipse.jemo.api.KeyValue;
import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.JemoPluginManager;
import org.eclipse.jemo.sys.internal.JarEntry;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MicroProfileConfigTest extends JemoGSMTest {
	Lock sequential = new ReentrantLock();

	@Before
	public void setUp() throws Exception {
	    sequential.lock();
	}

	@After
	public void tearDown() throws Exception {
	    sequential.unlock();
	}
	
	static AtomicReference<String> configValue = new AtomicReference<>();
	static CountDownLatch WAIT_LATCH = new CountDownLatch(1);
	static CountDownLatch CHANGE_CONFIG_LATCH = new CountDownLatch(1);
	
	public static class TestFixedModule implements FixedModule {

		@Override
		public void processFixed(String location, String instanceId) throws Throwable {
			configValue.set(ConfigProvider.getConfig().getValue("config", String.class));
			WAIT_LATCH.countDown();
			CHANGE_CONFIG_LATCH.await(5, TimeUnit.SECONDS);
			configValue.set(ConfigProvider.getConfig().getValue("config", String.class));
			WAIT_LATCH.countDown();
		}
		
	}
	
	@Test
	public void testFixedModuleConfigAccess() throws Throwable {
		//1. let's set a jemo configuration value for our module.
		setModuleConfig(301, new KeyValue<>("config", "value"));
		//2. run our fixed process to grab the configured value
		uploadPlugin(301, 1.0, "MicroProfileConfigTestFixed", TestFixedModule.class);
		startFixedProcesses();
		WAIT_LATCH.await(5, TimeUnit.SECONDS);
		assertEquals("value", configValue.get());
		setModuleConfig(301, new KeyValue<>("config", "value2"));
		WAIT_LATCH = new CountDownLatch(1);
		CHANGE_CONFIG_LATCH.countDown();
		WAIT_LATCH.await(5, TimeUnit.SECONDS);
		assertEquals("value2", configValue.get());
	}
}
