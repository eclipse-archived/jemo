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

import org.eclipse.jemo.api.Frequency;
import org.eclipse.jemo.api.Module;
import org.eclipse.jemo.api.ModuleLimit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * This test will prove the functionality of the batch processor in various situations.
 * 
 * 1. (default) a module with an execution frequency of once per second and no limits set will run once every minute on at least one node of every location regardless of if an existing process is running. - COMPLETED
 * 2. a module with an execution frequency of once per second and a maximum per GSM of 1 will run once per minute across the entire GSM but will not execute if a process of this type is already running. - COMPLETED
 * 3. a module with an execution frequency of once per second and a maximum per instance of 1 will run once per second on each instance but will not run if the maximum has already been reached by processes already running. - COMPLETED
 * 4. a module with an execution frequency of once per second and a maximum per GSM of 2 will run every second until the maximum number of live processes is reached across the GSM. - COMPLETED
 * 5. a module with an execution frequency of once per second limited to the "TEST1" location will run once every second regardless of any existing processes. - COMPLETED
 * 6. a module with an execution frequency of once per second limited to the "TEST3" location will never run. - COMPLETED
 * 7. a module with an execution frequency of once every 30 seconds after 10 seconds should only be running once. - COMPLETED
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestBatchModule extends JemoGSMTest {
	private static final Map<String,List<TestFixedModule.ProcessData>> PROCESS_MONITOR = new ConcurrentHashMap<>();
	private static final Map<String,List<CountDownLatch>> PROCESS_START_EVENT_MONITOR = new ConcurrentHashMap<>();
	
	private static List<TestFixedModule.ProcessData> ACTIVE_PROCESSES(Class<? extends AbstractTestModule> module) {
		return PROCESS_MONITOR.getOrDefault(module.getName(), new ArrayList<>());
	}
	
	private static List<TestFixedModule.ProcessData> ACTIVE_PROCESSES(Class<? extends AbstractTestModule> module,String location) {
		return PROCESS_MONITOR.getOrDefault(module.getName(), new ArrayList<>()).stream().filter(p -> p.getLocation().equals(location)).collect(Collectors.toList());
	}
	
	private static List<TestFixedModule.ProcessData> ACTIVE_PROCESSES(String instanceId,Class<? extends AbstractTestModule> module) {
		return PROCESS_MONITOR.getOrDefault(module.getName(), new ArrayList<>()).stream().filter(p -> p.getInstanceId().equals(instanceId)).collect(Collectors.toList());
	}
	
	private static void waitForProcess(Class<? extends AbstractTestModule> module,TimeUnit unit, long waitFor) throws InterruptedException {
		List<CountDownLatch> monitorList = PROCESS_START_EVENT_MONITOR.get(module.getName());
		if(monitorList == null) {
			monitorList = new CopyOnWriteArrayList<>();
			PROCESS_START_EVENT_MONITOR.put(module.getName(),monitorList);
		}
		CountDownLatch latch = new CountDownLatch(1);	
		monitorList.add(latch);
		latch.await(waitFor, unit);
	}
	
	public abstract static class AbstractTestModule implements Module {

		private Logger log = null;
		private volatile boolean running = false;
		
		@Override
		public void construct(Logger logger, String name, int id, double version) {
			log = logger;
		}

		/** our batch should last for exactly 5 seconds **/
		@Override
		public void processBatch(String location, boolean isCloudLocation) throws Throwable {
			List<TestFixedModule.ProcessData> procList = PROCESS_MONITOR.get(getClass().getName());
			if(procList == null) {
				procList = new CopyOnWriteArrayList<>();
				PROCESS_MONITOR.put(getClass().getName(),procList);
			}
			final String procId = UUID.randomUUID().toString();
			procList.add(new TestFixedModule.ProcessData(procId, getInstanceId(), location));
			int ctr = 0;
			log.info(String.format("[%s] STARTED PROCESS ID: %s NUM ACTIVE %d",getClass().getSimpleName(),procId, procList.size()));
			List<CountDownLatch> waitingList = PROCESS_START_EVENT_MONITOR.getOrDefault(getClass().getName(),new ArrayList<>());
			synchronized(waitingList) {
				waitingList.stream()
					.findFirst()
					.ifPresent(latch -> {
						waitingList.remove(latch);
						latch.countDown();
					});
			}
			
			while(running && ctr < 25) {
				try { TimeUnit.SECONDS.sleep(1); }catch(InterruptedException irrEx) { break; }
				ctr++;
			}
			procList.removeIf(p -> p.getProcessId().equals(procId));
			log.info(String.format("[%s] FINISHED PROCESS ID: %s NUM ACTIVE %d",getClass().getSimpleName(),procId, procList.size()));
		}

		@Override
		public void stop() {
			running = false;
			//we should clear our processes.
			List<TestFixedModule.ProcessData> procList = PROCESS_MONITOR.get(getClass().getName());
			if(procList != null) {
				procList.clear();
			}
			PROCESS_MONITOR.remove(getClass().getName());
		}

		@Override
		public void start() {
			running = true;
		}
	}
	
	/** 1. (default) a module with an execution frequency of once per minute and no limits set will run once every minute on at least one node of every location regardless of if an existing process is running. **/
	public static class DefaultBatchModule extends AbstractTestModule {

		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setBatchFrequency(Frequency.of(TimeUnit.SECONDS, 1))
				.build();
		}
	}
	
	@Test
	public void testDefaultBatchModule() throws Throwable {
		uploadPlugin(70000, 1.0, DefaultBatchModule.class.getSimpleName(), TestFixedModule.ProcessData.class, DefaultBatchModule.class, AbstractTestModule.class, TestBatchModule.class);
		//wait 2 seconds (we expect to have 2 active processes one on TEST1 and the other on TEST2)
		TimeUnit.MILLISECONDS.sleep(2500);
		assertEquals(2, ACTIVE_PROCESSES(DefaultBatchModule.class).size());
		for(String location : locationList()) {
			assertEquals(1, ACTIVE_PROCESSES(DefaultBatchModule.class, location).size());
		}
	}
	
	/** 2. a module with an execution frequency of once per second and a maximum per GSM of 1 will run once per minute across the entire GSM but will not execute if a process of this type is already running. **/
	public static class OneBatchPerGSMPerSecond extends AbstractTestModule {

		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setBatchFrequency(Frequency.of(TimeUnit.SECONDS, 1))
				.setMaxActiveBatchesPerGSM(1)
				.build();
		}
	}
	
	@Test
	public void testOneBatchPerGSMPerSecond() throws Throwable {
		uploadPlugin(70001, 1.0, OneBatchPerGSMPerSecond.class.getSimpleName(), TestFixedModule.ProcessData.class, OneBatchPerGSMPerSecond.class, AbstractTestModule.class, TestBatchModule.class);
		//wait 2 seconds (we expect to have 2 active processes one on TEST1 and the other on TEST2)
		TimeUnit.MILLISECONDS.sleep(2500);
		assertEquals(1, ACTIVE_PROCESSES(OneBatchPerGSMPerSecond.class).size());
	}
	
	/** 3. a module with an execution frequency of once per second and a maximum per instance of 1 will run once per second on each instance but will not run if the maximum has already been reached by processes already running **/
	public static class OneBatchPerInstancePerSecond extends AbstractTestModule {

		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setBatchFrequency(Frequency.of(TimeUnit.SECONDS, 1))
				.setMaxActiveBatchesPerInstance(1)
				.build();
		}
	}
	
	@Test
	public void testOneBatchPerInstancePerSecond() throws Throwable {
		uploadPlugin(70002, 1.0, OneBatchPerInstancePerSecond.class.getSimpleName(), TestFixedModule.ProcessData.class, OneBatchPerInstancePerSecond.class, AbstractTestModule.class, TestBatchModule.class);
		//wait 2 seconds (we expect to have 2 active processes one on TEST1 and the other on TEST2)
		TimeUnit.MILLISECONDS.sleep(4000);
		assertEquals(instanceList().size(), ACTIVE_PROCESSES(OneBatchPerInstancePerSecond.class).size());
		for(String instance : instanceList()) {
			assertEquals(1, ACTIVE_PROCESSES(instance, OneBatchPerInstancePerSecond.class).size());
		}
	}
	
	/** 4. a module with an execution frequency of once per second and a maximum per GSM of 2 will run every second until the maximum number of live processes is reached across the GSM. **/
	public static class TwoBatchesPerGSM extends AbstractTestModule {

		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setBatchFrequency(Frequency.of(TimeUnit.SECONDS, 1))
				.setMaxActiveBatchesPerGSM(2)
				.build();
		}
	}
	
	@Test
	public void testTwoBatchesPerGSM() throws Throwable {
		uploadPlugin(70003, 1.0, TwoBatchesPerGSM.class.getSimpleName(), TestFixedModule.ProcessData.class, TwoBatchesPerGSM.class, AbstractTestModule.class, TestBatchModule.class);
		//wait 2 seconds (we expect to have 2 active processes one on TEST1 and the other on TEST2)
		waitForProcess(TwoBatchesPerGSM.class, TimeUnit.SECONDS, 2);
		assertEquals(1, ACTIVE_PROCESSES(TwoBatchesPerGSM.class).size());
		long start = System.currentTimeMillis();
		waitForProcess(TwoBatchesPerGSM.class, TimeUnit.SECONDS, 5);
		long end = System.currentTimeMillis();
		assertTrue(end - start >= TimeUnit.SECONDS.toMillis(1)); //make sure it took at least 1 second for the new process to start.
		assertEquals(2, ACTIVE_PROCESSES(TwoBatchesPerGSM.class).size());
		TimeUnit.MILLISECONDS.sleep(5000);
		assertEquals(2, ACTIVE_PROCESSES(TwoBatchesPerGSM.class).size());
	}
	
	/** 5. a module with an execution frequency of once per second limited to the "TEST1" location will run once every second regardless of any existing processes. **/
	public static class OnlyOnTEST1LocationOncePerSecond extends AbstractTestModule {

		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setBatchFrequency(Frequency.of(TimeUnit.SECONDS, 1))
				.setBatchLocations("TEST1")
				.setMaxActiveBatchesPerLocation(-1) //remove quantity of execution limits
				.build();
		}
	}
	
	@Test
	public void testOnlyOnTEST1LocationOncePerSecond() throws Throwable {
		uploadPlugin(70004, 1.0, OnlyOnTEST1LocationOncePerSecond.class.getSimpleName(), TestFixedModule.ProcessData.class, OnlyOnTEST1LocationOncePerSecond.class, AbstractTestModule.class, TestBatchModule.class);
		//wait 2 seconds (we expect to have 2 active processes one on TEST1 and the other on TEST2)
		waitForProcess(OnlyOnTEST1LocationOncePerSecond.class, TimeUnit.SECONDS, 5);
		assertEquals(1, ACTIVE_PROCESSES(OnlyOnTEST1LocationOncePerSecond.class,"TEST1").size());
		assertEquals(0, ACTIVE_PROCESSES(OnlyOnTEST1LocationOncePerSecond.class,"TEST2").size());
		waitForProcess(OnlyOnTEST1LocationOncePerSecond.class, TimeUnit.SECONDS, 5);
		assertEquals(2, ACTIVE_PROCESSES(OnlyOnTEST1LocationOncePerSecond.class,"TEST1").size());
		assertEquals(0, ACTIVE_PROCESSES(OnlyOnTEST1LocationOncePerSecond.class,"TEST2").size());
		waitForProcess(OnlyOnTEST1LocationOncePerSecond.class, TimeUnit.SECONDS, 5);
		assertEquals(3, ACTIVE_PROCESSES(OnlyOnTEST1LocationOncePerSecond.class,"TEST1").size());
		assertEquals(0, ACTIVE_PROCESSES(OnlyOnTEST1LocationOncePerSecond.class,"TEST2").size());
	}
	
	/** 6. a module with an execution frequency of once per second limited to the "TEST3" location will never run. **/
	public static class OnlyOnTEST3LocationOncePerSecond extends AbstractTestModule {

		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setBatchFrequency(Frequency.of(TimeUnit.SECONDS, 1))
				.setBatchLocations("TEST3")
				.setMaxActiveBatchesPerLocation(-1) //remove quantity of execution limits
				.build();
		}
	}
	
	@Test
	public void testOnlyOnTEST3LocationOncePerSecond() throws Throwable {
		uploadPlugin(70005, 1.0, OnlyOnTEST3LocationOncePerSecond.class.getSimpleName(), TestFixedModule.ProcessData.class, OnlyOnTEST3LocationOncePerSecond.class, AbstractTestModule.class, TestBatchModule.class);
		//wait 2 seconds (we expect to have 2 active processes one on TEST1 and the other on TEST2)
		waitForProcess(OnlyOnTEST3LocationOncePerSecond.class, TimeUnit.SECONDS, 5);
		assertEquals(0, ACTIVE_PROCESSES(OnlyOnTEST3LocationOncePerSecond.class).size());
	}
	
	/** 7. a module with an execution frequency of once every 30 seconds after 10 seconds should only be running once. **/
	public static class OnceEvery30Seconds extends AbstractTestModule {

		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setBatchFrequency(Frequency.of(TimeUnit.SECONDS, 30))
				.setMaxActiveBatchesPerLocation(-1) //remove quantity of execution limits
				.build();
		}
	}
	
	@Test
	public void testOnceEvery30Seconds() throws Throwable {
		uploadPlugin(70006, 1.0, OnceEvery30Seconds.class.getSimpleName(), TestFixedModule.ProcessData.class, OnceEvery30Seconds.class, AbstractTestModule.class, TestBatchModule.class);
		//wait 2 seconds (we expect to have 2 active processes one on TEST1 and the other on TEST2)
		waitForProcess(OnceEvery30Seconds.class, TimeUnit.SECONDS, 5);
		assertEquals(1, ACTIVE_PROCESSES(OnceEvery30Seconds.class).size());
		TimeUnit.SECONDS.sleep(10);
		assertEquals(1, ACTIVE_PROCESSES(OnceEvery30Seconds.class).size());
	}
}
