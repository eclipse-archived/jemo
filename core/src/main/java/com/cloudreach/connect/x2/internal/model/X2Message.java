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
package com.cloudreach.connect.x2.internal.model;

import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import com.cloudreach.connect.x2.AbstractX2;
import com.cloudreach.connect.x2.CC;
import static com.cloudreach.connect.x2.CC.toJSONString;
import com.cloudreach.connect.x2.api.KeyValue;
import com.cloudreach.connect.x2.api.ModuleLimit;
import static com.cloudreach.connect.x2.internal.model.CCMessage.LOCATION_ANYWHERE;
import static com.cloudreach.connect.x2.internal.model.CCMessage.LOCATION_LOCALLY;
import static com.cloudreach.connect.x2.internal.model.CCMessage.LOCATION_CLOUD;
import static com.cloudreach.connect.x2.internal.model.CCMessage.LOCATION_THIS;
import static com.cloudreach.connect.x2.sys.CCPluginManager.PLUGIN_ID;
import static com.cloudreach.connect.x2.sys.CCPluginManager.PLUGIN_VERSION;
import com.cloudreach.connect.x2.sys.CCPluginManager;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * this class will statically implement all the bindings back to the actual X2 engine for message interoperability.
 * 
 * @author christopher stura
 */
public class X2Message {
	public static final String getInstance() {
		return CCPluginManager.getServerInstance().getINSTANCE_ID();
	}
	
	public static final String getInstanceQueueUrl() {
		return CCPluginManager.getServerInstance().getINSTANCE_QUEUE_URL();
	}
	
	private static void prepareMessage(CCMessage message) {
		message.setSourceInstance(getInstanceQueueUrl());
		if(message.getSourceModuleClass() == null && CCPluginManager.getCurrentModule() != null) {
			message.setSourceModuleClass(CCPluginManager.getCurrentModule().getClass().getName());
			message.setSourcePluginId(CCPluginManager.getCurrentModuleMetaData().getId());
		}
		if(message.getModuleClass() == null && CCPluginManager.getCurrentModule() != null) {
			message.setModuleClass(CCPluginManager.getCurrentModule().getClass().getName());
			message.setPluginId(CCPluginManager.getCurrentModuleMetaData().getId());
		}
	}
	private static Pattern instanceLocationPattern = Pattern.compile("([a-z0-9]{8}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{12})");
	
	public static final void send(String location,CCMessage message) throws JsonProcessingException {
		prepareMessage(message);
		String locationQueue;
		String clusterLocation;
		switch (location) {
			case LOCATION_ANYWHERE:
				locationQueue = CCPluginManager.getServerInstance().getGLOBAL_QUEUE_URL();
				clusterLocation = "GLOBAL";
				break;
			case LOCATION_LOCALLY:
				locationQueue = CCPluginManager.getServerInstance().getLOCATION_QUEUE_URL();
				clusterLocation = CCPluginManager.getServerInstance().getLOCATION();
				break;
			case LOCATION_THIS:
				locationQueue = CCPluginManager.getServerInstance().getINSTANCE_QUEUE_URL();
				clusterLocation = CCPluginManager.getServerInstance().getLOCATION();
				break;
			default:
				//we need to lookup the queue url the location specified.
				Matcher m = instanceLocationPattern.matcher(location);
				String instanceId = null;
				if(m.find()) {
					instanceId = m.group(1);
				}
				if(location.startsWith("https://") || location.startsWith("/") || (instanceId != null && location.endsWith(instanceId))) {
					locationQueue = location;
					final String queueName = CloudProvider.getInstance().getRuntime().getQueueName(locationQueue);
					clusterLocation = queueName.substring(3,queueName.length()-37);
				} else {
					//this is not a smart way to get a list of queues we should actually ask the dynamodb table.
					locationQueue = CloudProvider.getInstance().getRuntime().getQueueId("CC-"+location+"-WORK-QUEUE");
					clusterLocation = location;
				}
				break;
		}
		if(locationQueue != null) {
			//we need to enhance the logic here as it is easier to route messages at origin to the correct destinations
			//than it is to do it after the fact.
			//1. any routing algorithm only applies to messages not going to module id 0 (that is the system module)
			//2. we will check if the module named as the reciever actually exists in the system and that it implements event patterns (if not we drop the message)
			//3. we will check how many of these are actually running on each of the target instances before routing (so we spread things evenly).
			//4. we will not verify compliance limits as those should be verified on the singular instances and if the execution limit is reached messages should have their delivery delayed locally.
			if(message.getPluginId() != 0) {
				Map<String, String> instanceLocationMap = CCPluginManager.getServerInstance().getPluginManager().getActiveLocationList().stream()
					.flatMap(loc -> CCPluginManager.getServerInstance().getPluginManager().listInstances(loc).stream()
						.map(inst -> new KeyValue<>(inst,loc))
					)
					.collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
				
				Map<String,Integer> gsmExecutionMap = instanceLocationMap.entrySet().stream()
					.filter(e -> clusterLocation.equals("GLOBAL") || e.getValue().equalsIgnoreCase(clusterLocation))
					.filter(e -> Arrays.asList(CCPluginManager.getServerInstance().getPluginManager().getModuleList(e.getKey())).stream()
						.anyMatch(m -> m.getId() == message.getPluginId() && m.getVersion() == message.getPluginVersion() && m.getImplementation().equals(message.getModuleClass()))
					)
					.map(e -> new KeyValue<>(e.getKey(), CCPluginManager.getNumModuleEventsRunning(e.getKey(), message.getPluginId(), message.getPluginVersion(), message.getModuleClass())))
					.collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));

				//the execution map will have a list of valid locations (instance id's) in the target location and we will pick the one with the least amount of processes running.
				locationQueue = gsmExecutionMap.entrySet().stream()
					.min((e1,e2) -> e1.getValue().compareTo(e2.getValue()))
					.map(e -> CloudProvider.getInstance().getRuntime().getQueueId("CC-"+instanceLocationMap.get(e.getKey())+"-"+e.getKey()))
					.orElse(locationQueue); //messages will always be sent however if no valid instance can be found they will be sent to the target location
				final String fLocationQueue = locationQueue;
				CCPluginManager.getServerInstance().LOG(Level.INFO, "[X2Message][SEND] Selected %s current executing %d messages, execution map %s",locationQueue, gsmExecutionMap.entrySet().stream()
					.filter(e -> CloudProvider.getInstance().getRuntime().getQueueId("CC-"+instanceLocationMap.get(e.getKey())+"-"+e.getKey()).equalsIgnoreCase(fLocationQueue)).map(e -> e.getValue()).findAny().orElse(0), gsmExecutionMap.toString());
			}
			transmitMessage(message, locationQueue, location);
		}
	}
	
	protected static final void transmitMessage(CCMessage message,String queueId,String location) throws JsonProcessingException {
		boolean sendMessage = true;
		if(message.getPluginVersion() == 0 && message.getPluginId() != 0) {
			//we need to find the largest plugin version deployed.
			message.setPluginVersion(CCPluginManager.getServerInstance().getPluginManager().getApplicationList().stream()
					.filter(app -> PLUGIN_ID(app.getId()) == message.getPluginId() && app.getEvents().contains(message.getModuleClass()))
					.map(app -> PLUGIN_VERSION(app.getId()))
					.max((v1,v2) -> v1.compareTo(v2))
					.orElse(1.0)
				);
		}
		if(message.getPluginId() != 0 && CCPluginManager.getServerInstance().getPluginManager().getApplicationList()
					.stream()
					.anyMatch(app -> PLUGIN_ID(app.getId()) == message.getPluginId() && message.getPluginVersion() == PLUGIN_VERSION(app.getId()) && app.getEvents().contains(message.getModuleClass()))) {
			boolean isMessageValidForLocation = true;
			X2ApplicationMetaData app = CCPluginManager.getServerInstance().getPluginManager().getApplication(message.getPluginId(), message.getPluginVersion());
			if(app != null) {
				ModuleLimit appLimits = app.getLimits().get(message.getModuleClass());
				if(appLimits != null && appLimits.getEventLocations() != null) {
					isMessageValidForLocation = Arrays.asList(appLimits.getEventLocations()).contains(location);
				}
			}
			if(isMessageValidForLocation) {
				//if the queue id does not end with WORK-QUEUE then we need to check if the target instance will actually process it.
				if(!queueId.toUpperCase().endsWith("-WORK-QUEUE")) {
					final String queueName = CloudProvider.getInstance().getRuntime().getQueueName(queueId);
					final String instanceId = queueName.substring(queueName.length()-36);
					if(!Arrays.asList(CCPluginManager.getServerInstance().getPluginManager().getModuleList(instanceId)).stream()
						.anyMatch(m -> m.getId() == message.getPluginId() && m.getVersion() == message.getPluginVersion() && m.getImplementation().equals(message.getModuleClass()))) {
						sendMessage = false;
					}
				}
			} else {
				sendMessage = false;
			}
		} else if(message.getPluginId() != 0 && !message.getModuleClass().equals(CC.class.getName())) {
			sendMessage = false;
		}
		if(sendMessage) {
			try {
				if(queueId.equals(CCPluginManager.getServerInstance().getINSTANCE_QUEUE_URL())) {
					CC.log("[MESSAGE SENT]{"+queueId+"} - Message Processed Locally because the destination is local", Level.FINE);
					CCPluginManager.getServerInstance().sys_getInstanceQueueListener().scheduleMessage(message);
				} else {
					CC.log("[MESSAGE SENT]{"+queueId+"} - MessageID: "+CloudProvider.getInstance().getRuntime().sendMessage(queueId, CC.toJSONString(message)),Level.FINE);
				}
			}catch(QueueDoesNotExistException qNfEx) {
				CC.log(Level.WARNING, "[MESSAGE FAILED]{%s} - Message: %s",queueId, CC.toJSONString(message));
			}
		}
	}
	
	public static final void broadcast(String location,CCMessage message) throws JsonProcessingException {
		prepareMessage(message);
		List<String> queueUrls;
		try {
			if(location.equals(LOCATION_ANYWHERE)) {
				queueUrls = CloudProvider.getInstance().getRuntime().listQueueIds(null);
			} else if(location.equals(LOCATION_LOCALLY)) {
				queueUrls = CloudProvider.getInstance().getRuntime().listQueueIds(CCPluginManager.getServerInstance().getLOCATION());
			} else if(location.equals(LOCATION_CLOUD)) {
				queueUrls = new ArrayList<>();
				Arrays.asList(CC.CLOUD_LOCATIONS).forEach(l -> queueUrls.addAll(CloudProvider.getInstance().getRuntime().listQueueIds(l)));
			} else {
				queueUrls = CloudProvider.getInstance().getRuntime().listQueueIds(location);
			}
		}catch(Throwable ex) {
			CC.log(Level.WARNING, "[%s] no destinations for the message could be found because of error %s. message broadcast request ignored.", location, ex.getMessage());
			return;
		}
		final String msgJson = toJSONString(message);
		if(!queueUrls.contains(CCPluginManager.getServerInstance().getINSTANCE_QUEUE_URL())) {
			switch(location) {
				case LOCATION_ANYWHERE:
				case LOCATION_LOCALLY:
					queueUrls.add(CCPluginManager.getServerInstance().getINSTANCE_QUEUE_URL());
					break;
				case LOCATION_CLOUD:
					if(Arrays.asList(CC.CLOUD_LOCATIONS).stream().anyMatch(cl -> CCPluginManager.getServerInstance().getINSTANCE_QUEUE_URL().contains("/CC-"+cl.toUpperCase()))) {
						queueUrls.add(CCPluginManager.getServerInstance().getINSTANCE_QUEUE_URL());
					}
					break;
				default:
					if(CCPluginManager.getServerInstance().getINSTANCE_QUEUE_URL().toUpperCase().contains("/CC-"+location.toUpperCase())) {
						queueUrls.add(CCPluginManager.getServerInstance().getINSTANCE_QUEUE_URL());
					}
			}
		}
		//make sure we are not sending to dead instances.
		Set<String> activeInstances = CCPluginManager.getServerInstance().getPluginManager().getActiveLocationList().stream()
			.flatMap(l -> CCPluginManager.getServerInstance().getPluginManager().listInstances(l).stream().map(inst -> "CC-"+l+"-"+inst))
			.collect(Collectors.toSet());

		queueUrls.removeIf(q -> !activeInstances.contains(CloudProvider.getInstance().getRuntime().getQueueName(q)));
		
		queueUrls.parallelStream().forEach((q) -> {
			try {
				String queueName = CloudProvider.getInstance().getRuntime().getQueueName(q);
				transmitMessage(message, q, queueName.substring(3,queueName.length()-37));
			}catch(Throwable ex) {
				CC.log(Level.FINE, "[%s][%s] could not broadcast message the queue probably does not exist: message body: %s error %s", q, location, msgJson, CCError.toString(ex));
			}
		});
		CC.log(Level.FINE, "broadcasted to [%s] - %s", location, msgJson);
	}
}
