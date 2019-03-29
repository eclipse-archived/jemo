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

import org.eclipse.jemo.api.Module;
import org.eclipse.jemo.api.ModuleLimit;
import org.eclipse.jemo.sys.JemoPluginManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.junit.Assert.*;

import org.junit.Test;

/**
 * this test will run scenarios against a virtual memory based GSM
 * consisting of 2 different locations and validate that fixed processes behave as expected
 * on the Jemo platform.
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestFixedModule extends JemoGSMTest {
	
	public static class ProcessData {
		private final String processId;
		private final String instanceId;
		private final String location;
		private final long launchedOn = System.currentTimeMillis();
		
		protected ProcessData(String processId,String instanceId,String location) {
			this.processId = processId;
			this.instanceId = instanceId;
			this.location = location;
		}

		public String getProcessId() {
			return processId;
		}

		public String getInstanceId() {
			return instanceId;
		}

		public String getLocation() {
			return location;
		}

		public long getLaunchedOn() {
			return launchedOn;
		}
	}
	
	private static final Map<String,List<ProcessData>> ACTIVE_MODULES = new ConcurrentHashMap<>();
	private static final Set<String> ACTIVE_LOCATIONS = new ConcurrentSkipListSet<>();
	
	private static volatile String DEFAULT_PROCESS_INSTANCE_ID = null;
	
	private static synchronized void addActiveModule(Class cls,String processId,String instanceId,String location) {
		List<ProcessData> modList = ACTIVE_MODULES.get(cls.getName());
		if(modList == null) {
			modList = new ArrayList<>();
			ACTIVE_MODULES.put(cls.getName(),modList);
		}
		modList.add(new ProcessData(processId, instanceId, location));
	}
	
	private static synchronized void removeActiveModule(Class cls,String processId) {
		List<ProcessData> modList = ACTIVE_MODULES.get(cls.getName());
		if(modList != null) {
			modList.removeIf(p -> p.getProcessId().equals(processId));
		}
	}
	
	public static class DefaultFixedModule implements Module {
		
		private static final String MOD_ID = UUID.randomUUID().toString();

		private volatile boolean started = false;
		
		private String instanceId = null;
		private Logger moduleLogger = null;
		
		@Override
		public void construct(Logger logger, String name, int id, double version) {
			this.moduleLogger = logger;
		}

		@Override
		public void processFixed(String location, String instanceId) throws Throwable {
			//we will run this process 
			if(this.instanceId == null) {
				this.instanceId = instanceId;
			}
			
			addActiveModule(getClass(), MOD_ID, instanceId, location);
			if(DEFAULT_PROCESS_INSTANCE_ID == null) {
				DEFAULT_PROCESS_INSTANCE_ID = instanceId;
			}
			
			while(started) {
				Thread.sleep(100); //wait at 100 millisecond intervals but effectively do nothing
			}
		}

		@Override
		public void stop() {
			started = false;
			removeActiveModule(getClass(),MOD_ID);
			moduleLogger.log(Level.INFO, "Stopped "+MOD_ID+" the default process instance is "+DEFAULT_PROCESS_INSTANCE_ID);
			if(DEFAULT_PROCESS_INSTANCE_ID.equals(instanceId)) {
				DEFAULT_PROCESS_INSTANCE_ID = null;
			}
		}

		@Override
		public void start() {
			started = true;
		}
	}
	
	/**
	 * this test will verify that when a fixed module is deployed by default
	 * it will should only be active one across the GSM instance.
	 */
	@Test
	public void testDefaultFixedModule() throws Throwable {
		//we now need to upload this jar file to the GSM cluster.
		uploadModule(90000, 1.0, DefaultFixedModule.class.getSimpleName(), TestFixedModule.class, DefaultFixedModule.class);
		
		//after the upload we of course need to simulate the fact that the batch processor has been run on the base plugin module
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		
		//at this point we would expect a single fixed module instance to appear in the cluster.
		assertEquals(1, ACTIVE_MODULES.get(DefaultFixedModule.class.getName()).size());
		
		//the next test to prove this works is the look at recovery of execution. so what we want todo is kill the server where this instance is running
		//and then make sure it shows up on one of the other servers.
		assertNotNull(DEFAULT_PROCESS_INSTANCE_ID);
		stopServerByInstanceId(DEFAULT_PROCESS_INSTANCE_ID);
		assertNull(DEFAULT_PROCESS_INSTANCE_ID);
		
		//now we should run the batch again and the fixed module should re-appear.
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		assertEquals(1, ACTIVE_MODULES.get(DefaultFixedModule.class.getName()).size());
	}
	
	/**
	 * ok now of course fixed modules have different scenarios based on their limits
	 * and we will need to test each of the scenarios.
	 * 
	 * the scenarios are the following:
	 * 
	 * 1. we have 1 fixed module running in each location. - COMPLETED
	 * 2. we have 1 fixed module running on each instance - COMPLETED
	 * 3. we have 1 fixed module running on each GSM (default scenario) - COMPLETED
	 * 4. we have 2 fixed modules running on each location (we expect the distribution to be even across instances in each location) - COMPLETED
	 * 5. we have 2 fixed modules running on each GSM (we expect the there to be 2 processes running on different instances) - COMPLETED
	 * 6. we have 2 fixed modules running on each instance (we expect a total of 2 per instance and the total to be 2 * the number of instances in the GSM). - COMPLETED
	 * 7. we have 1 fixed module running only on the "TEST1" location (we expect 1 process to be running on an instance in "TEST1") - COMPLETED
	 * 8. we have 4 fixed modules running only on the "TEST2" location (we expect the processes to be evenly distributed in the instances on "TEST2") for the GSM. - COMPLETED
	 * 9. we have 4 fixed modules running on an invalid location. "TEST3" (we expect no processes to be running) - COMPLETED
	 * 10. we have 1 process per location running on the "TEST1" location (we expect only 1 process to be running on an instance in "TEST1") - COMPLETED
	 * 11. we have 2 process per location running on the "TEST3" location (we expect no processes to be running) - COMPLETED
	 */
	
	public abstract static class AbstractFixedModuleTest implements Module {
		private Logger logger = null;
		private boolean started = false;
		
		@Override
		public void construct(Logger logger, String name, int id, double version) { this.logger = logger; }

		@Override
		public void processFixed(String location, String instanceId) throws Throwable {
			ACTIVE_LOCATIONS.add(location);
			final String ID = UUID.randomUUID().toString();
			addActiveModule(getClass(), ID, instanceId, location);
			logger.info("Fixed process started at LOCATION: "+location+" instance: "+instanceId+" active modules: "+ACTIVE_MODULES.get(getClass().getName()).size());
			while(started) {
				Thread.sleep(100); //wait at 100 millisecond intervals but effectively do nothing
			}
		}
		
		@Override
		public void stop() {
			started = false;
		}

		@Override
		public void start() {
			started = true;
		}
	}
	
	public static class OneFixedPerLocation extends AbstractFixedModuleTest {
		
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveFixedPerLocation(1)
				.build();
		}
	}
	
	/** 1. we have 1 fixed module running in each location. **/
	@Test
	public void testRun1FixedModuleInEachLocation() throws Throwable {
		//we now need to upload this jar file to the GSM cluster.
		uploadModule(90001, 1.0, OneFixedPerLocation.class.getSimpleName(), TestFixedModule.class, OneFixedPerLocation.class, AbstractFixedModuleTest.class);
		
		//after the upload we of course need to simulate the fact that the batch processor has been run on the base plugin module
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		
		assertEquals(2, ACTIVE_MODULES.get(OneFixedPerLocation.class.getName()).size());
		assertEquals(2, ACTIVE_LOCATIONS.size());
	}
	
	public static class OneFixedPerInstance extends AbstractFixedModuleTest {

		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveFixedPerInstance(1)
				.build();
		}
		
	}
	
	/** 2. we have 1 fixed module running on each instance **/
	@Test
	public void testRun1FixedModuleInEachInstance() throws Throwable {
		uploadModule(90002, 1.0, OneFixedPerInstance.class.getSimpleName(), TestFixedModule.class, AbstractFixedModuleTest.class, OneFixedPerInstance.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		
		assertEquals(instanceList().size(), ACTIVE_MODULES.get(OneFixedPerInstance.class.getName()).size()); //we must have one active instance per instance.
		//now make sure we don't have more than one running on each instance.
		assertEquals(instanceList().size(), ACTIVE_MODULES.get(OneFixedPerInstance.class.getName()).stream().map(ProcessData::getInstanceId).distinct().count());
	}
	
	public static class TwoFixedPerLocation extends AbstractFixedModuleTest {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveFixedPerLocation(2)
				.build();
		}
	}
	
	/** 4. we have 2 fixed modules running on each location (we expect the distribution to be even across instances in each location) **/
	@Test
	public void testRun2FixedModuleInEachLocation() throws Throwable {
		uploadModule(90003, 1.0, TwoFixedPerLocation.class.getSimpleName(), TestFixedModule.class, AbstractFixedModuleTest.class, TwoFixedPerLocation.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		
		assertEquals(4, ACTIVE_MODULES.get(TwoFixedPerLocation.class.getName()).size());
		//we also expect them to be distributed evenly (we know that we will have either 1 or 2 instances in each location)
		for(String location : Arrays.asList("TEST1","TEST2")) {
			assertEquals(2, ACTIVE_MODULES.get(TwoFixedPerLocation.class.getName()).stream().filter(proc -> proc.getLocation().equals(location)).count()); //there must be 2 in each location
			switch(instanceList(location).size()) {
				case 1:
					for(String instanceId : instanceList(location)) {
						assertEquals(2, ACTIVE_MODULES.get(TwoFixedPerLocation.class.getName()).stream().filter(proc -> proc.getInstanceId().equals(instanceId)).count());
					}
					break;
				case 2:
					for(String instanceId : instanceList(location)) {
						assertEquals(1, ACTIVE_MODULES.get(TwoFixedPerLocation.class.getName()).stream().filter(proc -> proc.getInstanceId().equals(instanceId)).count());
					}
					break;
			}
		}
		
	}
	
	public static class TwoFixedPerGSM extends AbstractFixedModuleTest {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveFixedPerGSM(2)
				.build();
		}
	}
	
	/** 5. we have 2 fixed modules running on each GSM (we expect the there to be 2 processes running on different instances) **/
	@Test
	public void testRun2FixedModuleOnGSM() throws Throwable {
		uploadModule(90004, 1.0, TwoFixedPerGSM.class.getSimpleName(), TestFixedModule.class, AbstractFixedModuleTest.class, TwoFixedPerGSM.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		
		assertEquals(2, ACTIVE_MODULES.get(TwoFixedPerGSM.class.getName()).size());
		assertEquals(2, ACTIVE_MODULES.get(TwoFixedPerGSM.class.getName()).stream().map(proc -> proc.getInstanceId()).distinct().count());
	}
	
	public static class TwoFixedPerInstance extends AbstractFixedModuleTest {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveFixedPerInstance(2)
				.build();
		}
	}
	
	/** 6. we have 2 fixed modules running on each instance (we expect a total of 2 per instance and the total to be 2 * the number of instances in the GSM). **/
	@Test
	public void testRun2FixedModuleOnInstance() throws Throwable {
		uploadModule(90005, 1.0, TwoFixedPerInstance.class.getSimpleName(), TestFixedModule.class, AbstractFixedModuleTest.class, TwoFixedPerInstance.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		
		assertEquals(instanceList().size()*2, ACTIVE_MODULES.get(TwoFixedPerInstance.class.getName()).size());
		for(String instanceId : instanceList()) {
			assertEquals(2, ACTIVE_MODULES.get(TwoFixedPerInstance.class.getName()).stream().filter(proc -> proc.getInstanceId().equals(instanceId)).count());
		}
	}
	
	public static class OneRunningInTEST1Location extends AbstractFixedModuleTest {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveFixedPerLocation(1)
				.setFixedLocations("TEST1")
				.build();
		}
	}
	
	/** 7. we have 1 fixed module running only on the "TEST1" location (we expect 1 process to be running on an instance in "TEST1") **/
	@Test
	public void testRun1FixedModuleInTest1() throws Throwable {
		uploadModule(90006, 1.0, OneRunningInTEST1Location.class.getSimpleName(), TestFixedModule.class, AbstractFixedModuleTest.class, OneRunningInTEST1Location.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		
		assertEquals(1, ACTIVE_MODULES.get(OneRunningInTEST1Location.class.getName()).stream().filter(proc -> proc.getLocation().equals("TEST1")).count());
	}
	
	public static class FourFixedRunningInTEST2Location extends AbstractFixedModuleTest {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveFixedPerGSM(4)
				.setFixedLocations("TEST2")
				.build();
		}
	}
	
	/** 8. we have 4 fixed modules running only on the "TEST2" location (we expect the processes to be evenly distributed in the instances on "TEST2") for the GSM. **/
	@Test
	public void testFourFixedRunningInTEST2Location() throws Throwable {
		uploadModule(90007, 1.0, FourFixedRunningInTEST2Location.class.getSimpleName(), TestFixedModule.class, AbstractFixedModuleTest.class, FourFixedRunningInTEST2Location.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());

		assertEquals(4, ACTIVE_MODULES.get(FourFixedRunningInTEST2Location.class.getName()).stream().filter(proc -> proc.getLocation().equals("TEST2")).count());
		switch(instanceList("TEST2").size()) {
			case 1:
				for(String instanceId : instanceList("TEST2")) {
					assertEquals(4, ACTIVE_MODULES.get(FourFixedRunningInTEST2Location.class.getName()).stream().filter(proc -> proc.getInstanceId().equals(instanceId)).count());
				}
				break;
			case 2:
				for(String instanceId : instanceList("TEST2")) {
					assertEquals(2, ACTIVE_MODULES.get(FourFixedRunningInTEST2Location.class.getName()).stream().filter(proc -> proc.getInstanceId().equals(instanceId)).count());
				}
				break;
		}
	}
	
	public static class FourFixedRunningInTEST3Location extends AbstractFixedModuleTest {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveFixedPerGSM(4)
				.setFixedLocations("TEST3")
				.build();
		}
	}
	
	/** 9. we have 4 fixed modules running on an invalid location. "TEST3" (we expect no processes to be running) **/
	@Test
	public void testFourFixedRunningInTEST3Location() throws Throwable {
		uploadModule(90008, 1.0, FourFixedRunningInTEST3Location.class.getSimpleName(), TestFixedModule.class, AbstractFixedModuleTest.class, FourFixedRunningInTEST3Location.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		
		assertNull(ACTIVE_MODULES.get(FourFixedRunningInTEST3Location.class.getName()));
	}
	
	public static class OneInstanceInTEST1Location extends AbstractFixedModuleTest {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveFixedPerLocation(1)
				.setFixedLocations("TEST1")
				.build();
		}
	}
	
	/** 10. we have 1 process per location running on the "TEST1" location (we expect only 1 process to be running on an instance in "TEST1") **/
	@Test
	public void testOneInstanceInTEST1Location() throws Throwable {
		uploadModule(90009, 1.0, OneInstanceInTEST1Location.class.getSimpleName(), TestFixedModule.class, AbstractFixedModuleTest.class, OneInstanceInTEST1Location.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		
		assertEquals(1, ACTIVE_MODULES.get(OneInstanceInTEST1Location.class.getName()).size());
		assertEquals(1, ACTIVE_MODULES.get(OneInstanceInTEST1Location.class.getName()).stream()
			.filter(proc -> proc.getLocation().equals("TEST1"))
			.map(proc -> proc.getInstanceId())
			.distinct()
			.count()
		);
	}
	
	public static class TwoPerLocationInTEST3Location extends AbstractFixedModuleTest {
		@Override
		public ModuleLimit getLimits() {
			return ModuleLimit.newInstance()
				.setMaxActiveFixedPerLocation(2)
				.setFixedLocations("TEST3")
				.build();
		}
	}
	
	/** 11. we have 2 process per location running on the "TEST3" location (we expect no processes to be running) **/
	@Test
	public void testTwoPerLocationInTEST3Location() throws Throwable {
		uploadModule(90010, 1.0, TwoPerLocationInTEST3Location.class.getSimpleName(), TestFixedModule.class, AbstractFixedModuleTest.class, TwoPerLocationInTEST3Location.class);
		processBatch(0, 1.0, JemoPluginManager.PluginManagerModule.class.getName());
		
		assertNull(ACTIVE_MODULES.get(TwoPerLocationInTEST3Location.class.getName()));
	}
}
