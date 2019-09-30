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

import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;

import org.eclipse.jemo.JemoGSMTest;
import org.eclipse.jemo.api.FixedModule;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * this test will implement a complete wire based test for both OpenMetrics text format
 * and the Microprofile defined JSON metrics format. We will use Jemo behind the scenes
 * so we can make sure that Jemo is in-fact responding to the correct output formats expected
 * by consumers.
 * 
 * We will also embed test Jemo applications in this test so we can test through the various programming models
 * for application level metrics defined within the specification.
 * 
 * @author Christopher Stura
 */
public class MicroProfileMetricsTest extends JemoGSMTest {
	Lock sequential = new ReentrantLock();

	@Before
	public void setUp() throws Exception {
	    sequential.lock();
	}

	@After
	public void tearDown() throws Exception {
	    sequential.unlock();
	}
	
	static CountDownLatch EXEC_LATCH = new CountDownLatch(1);
	
	public static class MetricsModule implements FixedModule {

		@Override
		public void processFixed(String location, String instanceId) throws Throwable {
			// TODO Auto-generated method stub
			EXEC_LATCH.countDown();
		}
		
	}
	
	@Test
	public void testWireBase() throws Throwable {
		final int appId = 42;
		final double appVersion = 1.0;
		
		//in the first test we will attempt to call the plugin via an internal virtualised http call through the plugin manager
		//where we know that the application does not exist. we expect an error 404 to be returned (not found)
		HttpResponse response = callApplicationViaHTTP(appId, appVersion, "/metrics");
		assertEquals(404, response.getStatus());
		
		//next we need to build a test application and based on the specification we should return base metrics via HTTP
		//in the microprofile specification this happens for all applications regardless and the metrics that are returned are
		//interesting and valid for Jemo as a whole so they will always be present.
		uploadPlugin(appId, appVersion, "MicroProfileMetrics", MetricsModule.class);
		EXEC_LATCH = runFixedApplication(appId, appVersion, EXEC_LATCH);
		response = callApplicationViaHTTP(appId, appVersion, "/metrics");
		printEndpointMap();
		assertEquals(200, response.getStatus());
		
		//our application does not have an application specific metrics however we would expect the base metrics to be available.
		jemoServer.LOG(response.getResponseAsString(), Level.INFO);
		Map<String,Map<String,Object>> allMetrics = response.getResponse(Map.class);
		assertNotNull(allMetrics);
		Map<String,Object> baseMetrics = allMetrics.get("base");
		assertNotNull(baseMetrics);
		assertNotNull(baseMetrics.get("memory.usedHeap"));
		assertNotNull(baseMetrics.get("memory.committedHeap"));
		assertNotNull(baseMetrics.get("memory.maxHeap"));
		assertEquals(2, baseMetrics.keySet().stream().filter(k -> k.startsWith("gc.total")).count());
		assertEquals(2, baseMetrics.keySet().stream().filter(k -> k.startsWith("gc.time")).count());
		assertNotNull(baseMetrics.get("jvm.uptime"));
		assertNotNull(baseMetrics.get("thread.count"));
		assertNotNull(baseMetrics.get("thread.daemon.count"));
		assertNotNull(baseMetrics.get("thread.max.count"));
		assertNotNull(baseMetrics.get("threadpool.activeThreads;pool=WORK_EXECUTOR"));
		assertNotNull(baseMetrics.get("threadpool.size;pool=WORK_EXECUTOR"));
		assertNotNull(baseMetrics.get("classloader.loadedClasses.count"));
		assertNotNull(baseMetrics.get("classloader.loadedClasses.total"));
		assertNotNull(baseMetrics.get("classloader.unloadedClasses.total"));
		assertNotNull(baseMetrics.get("cpu.availableProcessors"));
		assertNotNull(baseMetrics.get("cpu.systemLoadAverage"));
		assertNotNull(baseMetrics.get("cpu.processCpuLoad"));
	}
}
