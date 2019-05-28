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
package org.eclipse.jemo.internal.model;

import com.amazonaws.services.sqs.model.QueueDoesNotExistException;
import org.eclipse.jemo.Jemo;
import static org.eclipse.jemo.Jemo.toJSONString;

import org.eclipse.jemo.api.KeyValue;
import org.eclipse.jemo.api.ModuleLimit;

import static org.eclipse.jemo.sys.JemoPluginManager.PLUGIN_ID;
import static org.eclipse.jemo.sys.JemoPluginManager.PLUGIN_VERSION;
import org.eclipse.jemo.sys.JemoPluginManager;
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
 * this class will statically implement all the bindings back to the actual Jemo engine for message interoperability.
 * 
 * @author christopher stura
 */
public class ServerMessage {
	public static final String getInstance() {
		return JemoPluginManager.getServerInstance().getINSTANCE_ID();
	}
	
	public static final String getInstanceQueueUrl() {
		return JemoPluginManager.getServerInstance().getINSTANCE_QUEUE_URL();
	}
	
	private static void prepareMessage(JemoMessage message) {
		message.setSourceInstance(getInstanceQueueUrl());
		if(message.getSourceModuleClass() == null && JemoPluginManager.getCurrentModule() != null) {
			message.setSourceModuleClass(JemoPluginManager.getCurrentModule().getClass().getName());
			message.setSourcePluginId(JemoPluginManager.getCurrentModuleMetaData().getId());
		}
		if(message.getModuleClass() == null && JemoPluginManager.getCurrentModule() != null) {
			message.setModuleClass(JemoPluginManager.getCurrentModule().getClass().getName());
			message.setPluginId(JemoPluginManager.getCurrentModuleMetaData().getId());
		}
	}
	private static Pattern instanceLocationPattern = Pattern.compile("([a-z0-9]{8}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{4}-[a-z0-9]{12})");
	
	public static final void send(String location, JemoMessage message) throws JsonProcessingException {
		prepareMessage(message);
		String locationQueue;
		String clusterLocation;
		switch (location) {
			case JemoMessage.LOCATION_ANYWHERE:
				locationQueue = JemoPluginManager.getServerInstance().getGLOBAL_QUEUE_URL();
				clusterLocation = "GLOBAL";
				break;
			case JemoMessage.LOCATION_LOCALLY:
				locationQueue = JemoPluginManager.getServerInstance().getLOCATION_QUEUE_URL();
				clusterLocation = JemoPluginManager.getServerInstance().getLOCATION();
				break;
			case JemoMessage.LOCATION_THIS:
				locationQueue = JemoPluginManager.getServerInstance().getINSTANCE_QUEUE_URL();
				clusterLocation = JemoPluginManager.getServerInstance().getLOCATION();
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
					clusterLocation = queueName.substring(5,queueName.length()-37);
				} else {
					//this is not a smart way to get a list of queues we should actually ask the dynamodb table.
					locationQueue = CloudProvider.getInstance().getRuntime().getQueueId("JEMO-"+location+"-WORK-QUEUE");
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
				Map<String, String> instanceLocationMap = JemoPluginManager.getServerInstance().getPluginManager().getActiveLocationList().stream()
					.flatMap(loc -> JemoPluginManager.getServerInstance().getPluginManager().listInstances(loc).stream()
						.map(inst -> new KeyValue<>(inst,loc))
					)
					.collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));
				
				Map<String,Integer> gsmExecutionMap = instanceLocationMap.entrySet().stream()
					.filter(e -> clusterLocation.equals("GLOBAL") || e.getValue().equalsIgnoreCase(clusterLocation))
					.filter(e -> Arrays.asList(JemoPluginManager.getServerInstance().getPluginManager().getModuleList(e.getKey())).stream()
						.anyMatch(m -> m.getId() == message.getPluginId() && m.getVersion() == message.getPluginVersion() && m.getImplementation().equals(message.getModuleClass()))
					)
					.map(e -> new KeyValue<>(e.getKey(), JemoPluginManager.getNumModuleEventsRunning(e.getKey(), message.getPluginId(), message.getPluginVersion(), message.getModuleClass())))
					.collect(Collectors.toMap(KeyValue::getKey, KeyValue::getValue));

				//the execution map will have a list of valid locations (instance id's) in the target location and we will pick the one with the least amount of processes running.
				locationQueue = gsmExecutionMap.entrySet().stream()
					.min((e1,e2) -> e1.getValue().compareTo(e2.getValue()))
					.map(e -> CloudProvider.getInstance().getRuntime().getQueueId("JEMO-"+instanceLocationMap.get(e.getKey())+"-"+e.getKey()))
					.orElse(locationQueue); //messages will always be sent however if no valid instance can be found they will be sent to the target location
				final String fLocationQueue = locationQueue;
				JemoPluginManager.getServerInstance().LOG(Level.FINE, "[ServerMessage][SEND] Selected %s current executing %d messages, execution map %s",locationQueue, gsmExecutionMap.entrySet().stream()
					.filter(e -> CloudProvider.getInstance().getRuntime().getQueueId("JEMO-"+instanceLocationMap.get(e.getKey())+"-"+e.getKey()).equalsIgnoreCase(fLocationQueue)).map(e -> e.getValue()).findAny().orElse(0), gsmExecutionMap.toString());
			}
			transmitMessage(message, locationQueue, location);
		}
	}
	
	protected static final void transmitMessage(JemoMessage message, String queueId, String location) throws JsonProcessingException {
		boolean sendMessage = true;
		if(message.getPluginVersion() == 0 && message.getPluginId() != 0) {
			//we need to find the largest plugin version deployed.
			message.setPluginVersion(JemoPluginManager.getServerInstance().getPluginManager().getApplicationList().stream()
					.filter(app -> PLUGIN_ID(app.getId()) == message.getPluginId() && app.getEvents().contains(message.getModuleClass()))
					.map(app -> PLUGIN_VERSION(app.getId()))
					.max((v1,v2) -> v1.compareTo(v2))
					.orElse(1.0)
				);
		}
		if(message.getPluginId() != 0 && JemoPluginManager.getServerInstance().getPluginManager().getApplicationList()
					.stream()
					.anyMatch(app -> PLUGIN_ID(app.getId()) == message.getPluginId() && message.getPluginVersion() == PLUGIN_VERSION(app.getId()) && app.getEvents().contains(message.getModuleClass()))) {
			boolean isMessageValidForLocation = true;
			JemoApplicationMetaData app = JemoPluginManager.getServerInstance().getPluginManager().getApplication(message.getPluginId(), message.getPluginVersion());
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
					if(!Arrays.asList(JemoPluginManager.getServerInstance().getPluginManager().getModuleList(instanceId)).stream()
						.anyMatch(m -> m.getId() == message.getPluginId() && m.getVersion() == message.getPluginVersion() && m.getImplementation().equals(message.getModuleClass()))) {
						sendMessage = false;
					}
				}
			} else {
				sendMessage = false;
			}
		} else if(message.getPluginId() != 0 && !message.getModuleClass().equals(Jemo.class.getName())) {
			sendMessage = false;
		}
		if(sendMessage) {
			try {
				if(queueId.equals(JemoPluginManager.getServerInstance().getINSTANCE_QUEUE_URL())) {
					Jemo.log("[MESSAGE SENT]{"+queueId+"} - Message Processed Locally because the destination is local", Level.FINE);
					JemoPluginManager.getServerInstance().sys_getInstanceQueueListener().scheduleMessage(message);
				} else {
					Jemo.log("[MESSAGE SENT]{"+queueId+"} - MessageID: "+CloudProvider.getInstance().getRuntime().sendMessage(queueId, Jemo.toJSONString(message)),Level.FINE);
				}
			}catch(QueueDoesNotExistException qNfEx) {
				Jemo.log(Level.WARNING, "[MESSAGE FAILED]{%s} - Message: %s",queueId, Jemo.toJSONString(message));
			}
		}
	}
	
	public static final void broadcast(String location, JemoMessage message) throws JsonProcessingException {
		prepareMessage(message);
		List<String> queueUrls;
		try {
			if(location.equals(JemoMessage.LOCATION_ANYWHERE)) {
				queueUrls = CloudProvider.getInstance().getRuntime().listQueueIds(null);
			} else if(location.equals(JemoMessage.LOCATION_LOCALLY)) {
				queueUrls = CloudProvider.getInstance().getRuntime().listQueueIds(JemoPluginManager.getServerInstance().getLOCATION());
			} else if(location.equals(JemoMessage.LOCATION_CLOUD)) {
				queueUrls = new ArrayList<>();
				Arrays.asList(Jemo.CLOUD_LOCATIONS).forEach(l -> queueUrls.addAll(CloudProvider.getInstance().getRuntime().listQueueIds(l)));
			} else {
				queueUrls = CloudProvider.getInstance().getRuntime().listQueueIds(location);
			}
		}catch(Throwable ex) {
			Jemo.log(Level.WARNING, "[%s] no destinations for the message could be found because of error %s. message broadcast request ignored.", location, ex.getMessage());
			return;
		}
		final String msgJson = toJSONString(message);
		if(!queueUrls.contains(JemoPluginManager.getServerInstance().getINSTANCE_QUEUE_URL())) {
			switch(location) {
				case JemoMessage.LOCATION_ANYWHERE:
				case JemoMessage.LOCATION_LOCALLY:
					queueUrls.add(JemoPluginManager.getServerInstance().getINSTANCE_QUEUE_URL());
					break;
				case JemoMessage.LOCATION_CLOUD:
					if(Arrays.asList(Jemo.CLOUD_LOCATIONS).stream().anyMatch(cl -> JemoPluginManager.getServerInstance().getINSTANCE_QUEUE_URL().toUpperCase().contains("/JEMO-"+cl.toUpperCase()))) {
						queueUrls.add(JemoPluginManager.getServerInstance().getINSTANCE_QUEUE_URL());
					}
					break;
				default:
					if(JemoPluginManager.getServerInstance().getINSTANCE_QUEUE_URL().toUpperCase().contains("/JEMO-"+location.toUpperCase())) {
						queueUrls.add(JemoPluginManager.getServerInstance().getINSTANCE_QUEUE_URL());
					}
			}
		}
		//make sure we are not sending to dead instances.
		Set<String> activeInstances = JemoPluginManager.getServerInstance().getPluginManager().getActiveLocationList().stream()
			.flatMap(l -> JemoPluginManager.getServerInstance().getPluginManager().listInstances(l).stream().map(inst -> ("jemo-"+l+"-"+inst).toUpperCase()))
			.collect(Collectors.toSet());

		queueUrls.removeIf(q -> !activeInstances.contains(CloudProvider.getInstance().getRuntime().getQueueName(q).toUpperCase()));
		
		queueUrls.parallelStream().forEach((q) -> {
			try {
				String queueName = CloudProvider.getInstance().getRuntime().getQueueName(q);
				transmitMessage(message, q, queueName.substring(5,queueName.length()-37));
			}catch(Throwable ex) {
				Jemo.log(Level.FINE, "[%s][%s] could not broadcast message the queue probably does not exist: message body: %s error %s", q, location, msgJson, JemoError.toString(ex));
			}
		});
		Jemo.log(Level.FINE, "broadcasted to [%s] - %s", location, msgJson);
	}
}
