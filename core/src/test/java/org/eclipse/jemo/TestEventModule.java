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
import org.eclipse.jemo.api.KeyValue;
import org.eclipse.jemo.api.Module;
import org.eclipse.jemo.api.ModuleLimit;
import org.eclipse.jemo.internal.model.JemoMessage;
import org.eclipse.jemo.sys.internal.Util;
import java.util.logging.Logger;
import static org.eclipse.jemo.TestFixedModule.ProcessData;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * This unit test will prove that the event processing service works correctly.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestEventModule extends JemoGSMTest {
	
	private static final Map<String,List<ProcessData>> PROCESS_MAP = new ConcurrentHashMap<>();
	private static final AtomicInteger COUNTER = new AtomicInteger(1);
	
	//construct an abstract module to implement the premise.
	public static class AbstractTestModule implements Module {

		private Logger logger = null;
		private final List<String> PROCESS_LIST = new CopyOnWriteArrayList<>();
		private AtomicBoolean running = new AtomicBoolean(true);
		
		@Override
		public void construct(Logger logger, String name, int id, double version) {
			this.logger = logger;
		}

		@Override
		public JemoMessage process(JemoMessage message) throws Throwable {
			//register that we are running.
			logger.info("Processing message - counter "+COUNTER.getAndIncrement()+" number "+message.getAttributes().get("number"));
			final String procId = UUID.randomUUID().toString();
			List<ProcessData> procList = null;
			synchronized(PROCESS_MAP) {
				procList = PROCESS_MAP.get(getClass().getName());
				if(procList == null) {
					procList = new CopyOnWriteArrayList<>();
					PROCESS_MAP.put(getClass().getName(),procList);
				}
				ProcessData proc = new ProcessData(procId, message.getCurrentInstance(), message.getCurrentLocation());
				PROCESS_LIST.add(procId);
				procList.add(proc);
			}
			
			while(running.get()) {
				Thread.sleep(TimeUnit.SECONDS.toMillis(10)); //wait 10 seconds
			}
			procList.removeIf(p -> p.getProcessId().equals(procId));
			PROCESS_LIST.remove(procId);
			return null;
		}

		@Override
		public void stop() {
			running.set(false);
			List<ProcessData> procList = PROCESS_MAP.get(getClass().getName());
			if(procList != null) {
				procList.removeIf(p -> PROCESS_LIST.contains(p.getProcessId()));
			}
			PROCESS_LIST.clear();
		}
	}
	
	/**
	 * The test scenarios that we want to cover should address the new frequency and limits features introduced in 2.3
	 * 
	 * Premise: a module that processes messages where each message takes exactly 10 seconds to complete
	 * 
	 * 1. if we send 100 messages with no limit we expect all 100 messages to be in the "in processing" state before the 10 seconds expire. - COMPLETED
	 * 2. if we send 100 messages with a limit of 5 messages per instance we expect that after 5 seconds "20" messages are in the "in processing" state and that no instance has more than 5 messages in that state. - COMPLETED
	 * 3. if we send 100 messages with a limit of 10 messages per location we expect that after 5 seconds "20" messages are in the "in processing" state and that no location has more than 10 messages in that state. - COMPLETED
	 * 4. if we send 100 messages with a limit of 5 messages per GSM we expect that after 5 seconds "5" messages are in the "in processing" state. - COMPLETED
	 * 5. if we send 10 messages with no limit to location "TEST3" to a module which can only process on "TEST1" we expect no messages to be in the "in processing" state. - COMPLETED
	 * 6. if we send 10 messages with a limit of 5 messages per location which can only be processed on "TEST1" we expect 5 messages to be running on each instance in "TEST1" - COMPLETED
	 * 7. if we send 100 messages with a frequency limit of 1 message per second after 10 seconds we expect no more than 10 messages to be in the "in processing" state - COMPLETED
	 */
	
	public static class TestDefaultModule extends AbstractTestModule {}
	
	/** 1. if we send 100 messages with no limit we expect all 100 messages to be in the "in processing" state before the 10 seconds expire. **/
	@Test
	public void testDefault() throws Throwable {
		uploadModule(80000, 1.0, TestDefaultModule.class.getSimpleName(), TestEventModule.class, AbstractTestModule.class, TestDefaultModule.class);
		IntStream.range(0, 20).forEach(i -> Util.B(null, x -> sendMessage(80000, 1.0, TestDefaultModule.class, JemoMessage.LOCATION_ANYWHERE, KeyValue.of("number", i))));
		Thread.sleep(TimeUnit.SECONDS.toMillis(30));
		assertEquals(20,PROCESS_MAP.get(TestDefaultModule.class.getName()).size());
	}
	
	/** 2. if we send 100 messages with a limit of 5 messages per instance we expect that after 5 seconds "20" messages are in the "in processing" state and that no instance has more than 5 messages in that state. **/
	public static class TestGSMLimitOf5Module extends AbstractTestModule {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveEventsPerGSM(20)
				.build();
		}
	}
	
	@Test
	public void testGSMLimitOf5() throws Throwable {
		uploadModule(80001, 1.0, TestGSMLimitOf5Module.class.getSimpleName(), TestEventModule.class, AbstractTestModule.class, TestGSMLimitOf5Module.class);
		IntStream.range(0, 100).forEach(i -> Util.B(null, x -> {
			sendMessage(80001, 1.0, TestGSMLimitOf5Module.class, JemoMessage.LOCATION_ANYWHERE, KeyValue.of("number", i));
			Thread.sleep(100);
		}));
		Thread.sleep(TimeUnit.SECONDS.toMillis(20));
		assertEquals(20,PROCESS_MAP.get(TestGSMLimitOf5Module.class.getName()).size());
		for(String instanceId : PROCESS_MAP.get(TestGSMLimitOf5Module.class.getName()).stream()
			.map(p -> p.getInstanceId())
			.distinct()
			.collect(Collectors.toList())) {
			assertEquals("Instance Id: "+instanceId,5,PROCESS_MAP.get(TestGSMLimitOf5Module.class.getName()).stream().filter(p -> p.getInstanceId().equals(instanceId)).count());
		}
	}
	
	/** 3. if we send 100 messages with a limit of 10 messages per location we expect that after 5 seconds "20" messages are in the "in processing" state and that no location has more than 10 messages in that state. **/
	public static class TestLocationLimitOf10Module extends AbstractTestModule {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveEventsPerLocation(10)
				.build();
		}
	}
	
	@Test
	public void testLocationLimitOf10() throws Throwable {
		uploadModule(80002, 1.0, TestLocationLimitOf10Module.class.getSimpleName(), TestEventModule.class, AbstractTestModule.class, TestLocationLimitOf10Module.class);
		IntStream.range(0, 100).forEach(i -> Util.B(null, x -> {
			sendMessage(80002, 1.0, TestLocationLimitOf10Module.class, JemoMessage.LOCATION_ANYWHERE, KeyValue.of("number", i));
			Thread.sleep(100);
		}));
		Thread.sleep(TimeUnit.SECONDS.toMillis(20));
		assertEquals(20,PROCESS_MAP.get(TestLocationLimitOf10Module.class.getName()).size());
		for(String location : locationList()) {
			assertEquals(10,PROCESS_MAP.get(TestLocationLimitOf10Module.class.getName()).stream().filter(p -> p.getLocation().equals(location)).count());
		}
	}
	
	/** 4. if we send 100 messages with a limit of 5 messages per GSM we expect that after 5 seconds "5" messages are in the "in processing" state. **/
	public static class TestGSMGlobalLimitOf5Module extends AbstractTestModule {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveEventsPerGSM(5)
				.build();
		}
	}
	
	@Test
	public void testGSMGlobalLimitOf5() throws Throwable {
		uploadModule(80003, 1.0, TestGSMGlobalLimitOf5Module.class.getSimpleName(), TestEventModule.class, AbstractTestModule.class, TestGSMGlobalLimitOf5Module.class);
		IntStream.range(0, 100).forEach(i -> Util.B(null, x -> {
			sendMessage(80003, 1.0, TestGSMGlobalLimitOf5Module.class, JemoMessage.LOCATION_ANYWHERE, KeyValue.of("number", i));
		}));
		Thread.sleep(TimeUnit.SECONDS.toMillis(20));
		assertEquals(5,PROCESS_MAP.get(TestGSMGlobalLimitOf5Module.class.getName()).size());
	}
	
	/** 5. if we send 10 messages with no limit to location "TEST3" to a module which can only process on "TEST1" we expect no messages to be in the "in processing" state. **/
	public static class TestOnlyTEST1LocationModule extends AbstractTestModule {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setEventLocations("TEST1")
				.build();
		}
	}
	
	@Test
	public void testOnlyTEST1Location() throws Throwable {
		uploadModule(80004, 1.0, TestOnlyTEST1LocationModule.class.getSimpleName(), TestEventModule.class, AbstractTestModule.class, TestOnlyTEST1LocationModule.class);
		IntStream.range(0, 10).forEach(i -> Util.B(null, x -> {
			sendMessage(80004, 1.0, TestOnlyTEST1LocationModule.class, "TEST3", KeyValue.of("number", i));
		}));
		Thread.sleep(TimeUnit.SECONDS.toMillis(20));
		assertNull(PROCESS_MAP.get(TestOnlyTEST1LocationModule.class.getName()));
	}
	
	/** 6. if we send 20 messages with a limit of 5 messages per location which can only be processed on "TEST1" we expect 5 messages to be running on each instance in "TEST1" **/
	public static class TestOnlyTEST1LocationLimit5PerLocationModule extends AbstractTestModule {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setEventLocations("TEST1")
				.setMaxActiveEventsPerLocation(10)
				.build();
		}
	}
	
	@Test
	public void testOnlyTEST1LocationLimit5PerLocation() throws Throwable {
		uploadModule(80005, 1.0, TestOnlyTEST1LocationLimit5PerLocationModule.class.getSimpleName(), TestEventModule.class, AbstractTestModule.class, TestOnlyTEST1LocationLimit5PerLocationModule.class);
		IntStream.range(0, 20).forEach(i -> Util.B(null, x -> {
			sendMessage(80005, 1.0, TestOnlyTEST1LocationLimit5PerLocationModule.class, "TEST1", KeyValue.of("number", i));
			Thread.sleep(100);
		}));
		Thread.sleep(TimeUnit.SECONDS.toMillis(20));
		int numInstancesInTest1 = instanceList("TEST1").size();
		assertEquals(numInstancesInTest1*5,PROCESS_MAP.get(TestOnlyTEST1LocationLimit5PerLocationModule.class.getName()).size());
		assertEquals(numInstancesInTest1*5,PROCESS_MAP.get(TestOnlyTEST1LocationLimit5PerLocationModule.class.getName()).stream().filter(p -> p.getLocation().equals("TEST1")).count());
		assertEquals(0,PROCESS_MAP.get(TestOnlyTEST1LocationLimit5PerLocationModule.class.getName()).stream().filter(p -> !p.getLocation().equals("TEST1")).count());
		for(String instanceId : instanceList("TEST1")) {
			assertEquals(5,PROCESS_MAP.get(TestOnlyTEST1LocationLimit5PerLocationModule.class.getName()).stream().filter(p -> p.getInstanceId().equals(instanceId)).count());
		}
	}
	
	/** 7. if we send 100 messages with a frequency limit of 1 message per second after 10 seconds we expect no more than 10 messages to be in the "in processing" state **/
	public static class TestFrequency1PerSecondModule extends AbstractTestModule {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setEventFrequency(Frequency.of(TimeUnit.SECONDS, 1))
				.build();
		}
	}
	
	@Test
	public void testFrequency1PerSecond() throws Throwable {
		uploadModule(80006, 1.0, TestFrequency1PerSecondModule.class.getSimpleName(), TestEventModule.class, AbstractTestModule.class, TestFrequency1PerSecondModule.class);
		long start = System.currentTimeMillis();
		IntStream.range(0, 100).forEach(i -> Util.B(null, x -> {
			sendMessage(80006, 1.0, TestFrequency1PerSecondModule.class, JemoMessage.LOCATION_ANYWHERE, KeyValue.of("number", i));
		}));
		long end = System.currentTimeMillis();
		if((end-start) < TimeUnit.SECONDS.toMillis(10)) {
			Thread.sleep(TimeUnit.SECONDS.toMillis(10)-(end-start));
		}
		end = System.currentTimeMillis();
		long totalSeconds = (end-start)/1000;
		if(totalSeconds*1000 < (end-start)) {
			totalSeconds++;
		}
		assertTrue(PROCESS_MAP.get(TestFrequency1PerSecondModule.class.getName()).size() <= totalSeconds*instanceList().size());
	}
}
