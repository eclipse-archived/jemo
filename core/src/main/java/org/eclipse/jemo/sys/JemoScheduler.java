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
package org.eclipse.jemo.sys;

import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.api.KeyValue;
import org.eclipse.jemo.api.ModuleLimit;
import org.eclipse.jemo.internal.model.CloudProvider;
import org.eclipse.jemo.sys.internal.Util;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * this class will contain methods for running the scheduler engine.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class JemoScheduler extends Thread {
	
	public static final String STORAGE_SCHEDULER_NOMINEE = "SYS-SCHEDULER-NOMINEE";
	public static final String STORAGE_SCHEDULER_NOMINATEDON = "SYS-SCHEDULER-NOMINATION-TIMESTAMP";
	
	public static class Nomination {
		private final long nominatedOn;
		private final String instanceId;
		
		public Nomination(String instanceId,long nominatedOn) {
			this.nominatedOn = nominatedOn;
			this.instanceId = instanceId;
		}

		public long getNominatedOn() {
			return nominatedOn;
		}

		public String getInstanceId() {
			return instanceId;
		}
		
		public boolean isExpired() {
			long timeSinceNomination = System.currentTimeMillis() - nominatedOn;
			return timeSinceNomination > TimeUnit.MINUTES.toMillis(5);
		}
	}
	
	protected static class ModuleActivityMap {
		private final AbstractJemo jemoServer;
		private final JemoPluginManager.ModuleInfo module;
		private final Set<String> instances;
		private final ModuleLimit limits;
		
		public ModuleActivityMap(AbstractJemo jemoServer, JemoPluginManager.ModuleInfo module, Set<String> instances, ModuleLimit limits) {
			this.module = module;
			this.instances = instances;
			this.limits = limits;
			this.jemoServer = jemoServer;
		}
		
		public int getCurrentGSMActivity() {
			return jemoServer.getPluginManager().getNumModuleEventsRunningOnGSM(module.getId(), module.getVersion(), module.getImplementation());
		}
		
		public int getCurrentInstanceActivity(String instanceId) {
			return JemoPluginManager.getNumModuleEventsRunning(instanceId, module.getId(), module.getVersion(), module.getImplementation());
		}
		
		public int getCurrentLocationActivity(String location) {
			return jemoServer.getPluginManager().getNumModuleEventsRunningOnLocation(location,module.getId(), module.getVersion(), module.getImplementation());
		}
		
		public int getGSMMaximum() {
			return limits == null ? -1 : limits.getMaxActiveBatchesPerGSM();
		}
		
		public int getInstanceMaximum() {
			return limits == null ? -1 : limits.getMaxActiveBatchesPerInstance();
		}
		
		public int getLocationMaximum() {
			return limits == null ? -1 : limits.getMaxActiveBatchesPerLocation();
		}
		
		public boolean isValidLocation(String location) {
			return limits == null ? true : (limits.getBatchLocations() == null || limits.getBatchLocations().length == 0 ? true : Arrays.asList(limits.getBatchLocations()).contains(location));
		}
		
		public boolean isValidForFrequencyInterval() {
			if(limits == null) {
				return true;
			}
			if(limits.getBatchFrequency() == null) {
				return true;
			}
			long lastExecutionDate = jemoServer.getPluginManager().getLastLaunchedModuleEventOnGSM(module.getId(), module.getVersion(), module.getImplementation());
			if(lastExecutionDate == 0) {
				return true;
			}
			return (System.currentTimeMillis() - lastExecutionDate >= limits.getBatchFrequency().getUnit().toMillis(limits.getBatchFrequency().getValue()));
		}
	}
	
	private AbstractJemo jemoServer = null;
	private final Random NOMINATION_RND = new Random(System.currentTimeMillis());
	private Nomination currentNomination = null;
	private final AtomicBoolean RUNNING = new AtomicBoolean(false);
	
	public JemoScheduler(AbstractJemo jemoServer) {
		this.jemoServer = jemoServer;
	}

	@Override
	public synchronized void start() {
		RUNNING.set(true);
		super.start(); //To change body of generated methods, choose Tools | Templates.
	}
	
	public Nomination getCurrentNomination() {
		String nominee = CloudProvider.getInstance().getRuntime().retrieve(STORAGE_SCHEDULER_NOMINEE, String.class);
		Long LAST_NOMINATION = CloudProvider.getInstance().getRuntime().retrieve(STORAGE_SCHEDULER_NOMINATEDON, Long.class);
		if(nominee != null && LAST_NOMINATION != null) {
			long timeSinceNomination = System.currentTimeMillis() - LAST_NOMINATION;
			if(timeSinceNomination > TimeUnit.MINUTES.toMillis(5)) { //the scheduler will hold it's nomination for 5 minutes (this will make Jemo cheaper to run)
				//this means that the last nomination is stale because of the time in which the nomination was made.
				return null;
			} else if(!jemoServer.getPluginManager().isInstanceActive(nominee)) {
				return null;
			} else {
				return new Nomination(nominee, LAST_NOMINATION);
			}
		}
		
		return null;
	}
	
	/**
	 * this method will produce a new nominated instance. the instance that decides this nomination will be the instance
	 * with the lowest CRC 32 of their instance id, the actual nominated instance will be chosen randomly.
	 * 
	 * @return a reference to the new nomination only if we were selected as the nominated instance otherwise null.
	 */
	public Nomination newNomination() {
		Nomination nomination = null;
		Set<String> activeInstanceList = jemoServer.getPluginManager().getActiveInstanceList();
		String nominatingInstanceId = activeInstanceList.stream()
			.map(inst -> new KeyValue<>(inst, Util.crc(inst.getBytes(Util.UTF8_CHARSET))))
			.min((k1,k2) -> k1.getValue().compareTo(k2.getValue()))
			.map(KeyValue::getKey)
			.orElse(null);
		
		if(nominatingInstanceId != null) { //if at least 1 instance is active in the GSM.
			if(nominatingInstanceId.equals(jemoServer.getINSTANCE_ID())) {
				//we are the nominating instance
				int nominatedInstancePos = NOMINATION_RND.nextInt(activeInstanceList.size());
				final String nominatedInstanceId = (activeInstanceList.toArray(new String[] {}))[nominatedInstancePos];
				nomination = new Nomination(nominatedInstanceId, System.currentTimeMillis());
				CloudProvider.getInstance().getRuntime().store(STORAGE_SCHEDULER_NOMINEE, nominatedInstanceId);
				CloudProvider.getInstance().getRuntime().store(STORAGE_SCHEDULER_NOMINATEDON, nomination.getNominatedOn());
				
				if(nominatedInstanceId.equals(jemoServer.getINSTANCE_ID())) {
					return nomination;
				}
			}
		}
		
		return null;
	}

	@Override
	public void interrupt() {
		RUNNING.set(false);
		super.interrupt(); //To change body of generated methods, choose Tools | Templates.
		jemoServer.LOG(Level.INFO, "[%s][%s] THE SCHEDULER HAS BEEN INTERUPTED", getClass().getSimpleName(), jemoServer.getINSTANCE_ID());
	}

	@Override
	public void run() {
		while(RUNNING.get()) { //run as long as nobody has interrupted us.
			if (!jemoServer.isInInstallationMode()) {
				if (currentNomination == null) {
					currentNomination = getCurrentNomination();
				}
				if (currentNomination == null || currentNomination.isExpired()) {
					currentNomination = newNomination();
				}
			}
			if(currentNomination != null && currentNomination.getInstanceId().equals(jemoServer.getINSTANCE_ID())) {
				//1. we will need to know the module list for each active instance in the cluster.
				jemoServer.LOG(Level.INFO, "[%s][%s] NOMINATED SCHEDULER IS %s - RUNNING STATUS IS %s", getClass().getSimpleName(), jemoServer.getINSTANCE_ID(), currentNomination.getInstanceId(), String.valueOf(RUNNING.get()));
				//we need to know where things are being executed so we can filter out modules which are over their limits.
				Set<String> activeInstanceList = jemoServer.getPluginManager().getActiveInstanceList();
				Map<String,String> instanceLocationMap = jemoServer.getPluginManager().getInstanceLocationMap(activeInstanceList.toArray(new String[] {}));
				Map<String,List<JemoPluginManager.ModuleInfo>> instanceModuleMap = activeInstanceList
					.stream()
					.map(inst -> new KeyValue<>(inst,
						Arrays.asList(jemoServer.getPluginManager().getModuleList(inst)).stream()
							.filter(mod -> mod.isBatch())
							.collect(Collectors.toList())
					))
					.collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
				
				List<ModuleActivityMap> activityMap = instanceModuleMap.values().stream()
					.flatMap(List::stream)
					.map(m -> new ModuleActivityMap(jemoServer, m, activeInstanceList, jemoServer.getPluginManager().getModuleLimits(m.getId(), m.getVersion(),m.getImplementation())))
					.filter(act -> act.getGSMMaximum() == -1 || act.getCurrentGSMActivity() < act.getGSMMaximum()) //filter out any modules that have reached their GSM activity maximum
					.filter(act -> act.isValidForFrequencyInterval()) //remove modules with an incompatible frequency.
					.collect(Collectors.toList());
				
				//remove modules from the instance map that have incompatible activity.
				instanceModuleMap.values().forEach(mList -> mList.removeIf(m -> !activityMap.stream().anyMatch(act -> act.module.getId() == m.getId() && act.module.getImplementation().equals(m.getImplementation()))));
				
				//2. now we need an inverted map that will tell us the modules per instance (all module versions are available on all instances)
				Map<JemoPluginManager.ModuleInfo,Set<String>> moduleInstanceMap = instanceModuleMap.entrySet().stream()
					.flatMap(e -> e.getValue().stream().map(mod -> new AbstractMap.SimpleEntry<>(mod,e.getKey())))
					.collect(Collectors.groupingBy(AbstractMap.SimpleEntry::getKey,Collectors.mapping(AbstractMap.SimpleEntry::getValue, Collectors.toSet())));
				
				//3. we now need to figure out of the instances which can potentially run the batch which one we should use.
				Map<String,List<String>> moduleInstanceTargetMap = moduleInstanceMap.entrySet().stream()
					.filter(e -> !e.getValue().isEmpty())
					.map(e -> {
						final KeyValue<List<String>> result = new KeyValue<>(e.getKey().getImplementation(),new ArrayList<>());
						ModuleActivityMap modActivity = activityMap.stream().filter(act -> act.module.getImplementation().equals(e.getKey().getImplementation()) && act.module.getId() == e.getKey().getId())
							.findAny().orElse(null);
						if(modActivity != null) {
							if(modActivity.getInstanceMaximum() != -1) { //this means that we should have at least 1 per instance and possibly more if the maximum is higher.
								e.getValue().stream()
									.filter(inst -> modActivity.getInstanceMaximum() > modActivity.getCurrentInstanceActivity(inst)) //keep only the instances that are under their maximum
									.forEach(inst -> result.getValue().add(inst));
							} else if(modActivity.getLocationMaximum() != -1) {
								//we need to find the locations where the instances that can process this module are located.
								instanceLocationMap.entrySet().stream()
									.filter(me -> e.getValue().contains(me.getKey()))
									.map(me -> me.getValue())
									.distinct()
									.filter(l -> modActivity.isValidLocation(l))
									.filter(l -> modActivity.getLocationMaximum() > modActivity.getCurrentLocationActivity(l)) //remove any locations that have already reached their maximum
									.forEach(l -> result.getValue().add(l));
							} else {
								//we need to filter the available instances by those with a valid location for this module.
								String[] validInstances = e.getValue().stream()
									.filter(inst -> modActivity.isValidLocation(instanceLocationMap.get(inst)))
									.toArray(String[]::new);
								if(validInstances != null && validInstances.length > 0) {
									result.getValue().add(validInstances[NOMINATION_RND.nextInt(validInstances.length)]);
								}
							}
						} /*else {
							result.getValue().add((e.getValue().toArray(new String[] {}))[NOMINATION_RND.nextInt(e.getValue().size())]);
						}*/ //this should never happen.
						return result; //new KeyValue<>(e.getKey(), (e.getValue().toArray(new String[] {}))[NOMINATION_RND.nextInt(e.getValue().size())]);
					})
					.collect(Collectors.toMap(KeyValue::getKey,KeyValue::getValue));
				
				//4. now we can dispatch the execution messages since we know what to run and where to run it.
				List<String> instanceQueueUrlList = CloudProvider.getInstance().getRuntime().listQueueIds(null, false);
				moduleInstanceTargetMap.entrySet().stream()
					.flatMap(e -> e.getValue().stream().map(inst -> new KeyValue<>(inst,e.getKey())))
					.forEach(e -> {
					final String queueUrl = instanceQueueUrlList.stream().filter(qId -> qId.endsWith(e.getKey())).findFirst().orElse(e.getKey());
					if(queueUrl != null) {
						final int moduleId = instanceModuleMap.values().stream()
							.flatMap(List::stream)
							.filter(m -> m.getImplementation().equals(e.getValue()))
							.findAny()
							.map(m -> m.getId())
							.orElse(-1);
						if(moduleId != -1) {
							if(RUNNING.get()) {
								jemoServer.sendRunBatchMessage(moduleId, e.getValue(), queueUrl);
							}
						}
					}
				});
			}
			//now we should sleep for a bit (1 second) before trying again.
			try { TimeUnit.SECONDS.sleep(1); }catch(InterruptedException irrEx) {}
		}
	}
}
