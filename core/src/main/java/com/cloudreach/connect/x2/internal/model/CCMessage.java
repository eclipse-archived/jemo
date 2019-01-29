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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author christopher stura
 */
public class CCMessage {
	public static final String LOCATION_ANYWHERE = "GLOBAL";
	public static final String LOCATION_LOCALLY = "LOCAL";
	public static final String LOCATION_CLOUD = "CLOUD";
	public static final String LOCATION_THIS = "THIS";
	
	private String id = UUID.randomUUID().toString();
	private int pluginId = 0;
	private double pluginVersion = 0;
	private String moduleClass = null;
	private CCError lastError = null;
	private int executionCount = 0;
	private long lastRunTime = 0;
	private String lastRunInstance = null;
	private int sourcePluginId = 0;
	private double sourcePluginVersion = 0;
	private String sourceInstance = null;
	private String sourceModuleClass = null;
	private Map<String,Object> attributes = new HashMap<>();
	private String currentLocation = null;
	private String currentInstance = null;

	public CCMessage() {}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	/**
	 * the plugin id indicates the code context in which this code should be processed.
	 * CC will use the plugin id to route messages from the different buses to specific code segments.
	 * 
	 * @return the id of the plugin which the data in this message should be direct too.
	 */
	public int getPluginId() {
		return pluginId;
	}

	public int getSourcePluginId() {
		return sourcePluginId;
	}

	public void setSourcePluginId(int sourcePluginId) {
		this.sourcePluginId = sourcePluginId;
	}

	public String getSourceInstance() {
		return sourceInstance;
	}

	public void setSourceInstance(String sourceInstance) {
		this.sourceInstance = sourceInstance;
	}
	
	public Map<String, Object> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, Object> attributes) {
		this.attributes.clear();
		this.attributes.putAll(attributes);
	}

	public void setPluginId(int pluginId) {
		this.pluginId = pluginId;
	}

	@JsonProperty(value = "lasterror")
	public CCError getLastError() {
		return lastError;
	}

	@JsonProperty(value = "lasterror")
	public void json_setLastError(CCError lastError) {
		this.lastError = lastError;
	}
	
	@JsonIgnore
	public void setLastError(CCError lastError) {
		this.lastError = lastError;
		this.executionCount++;
		this.lastRunInstance = getInstanceID();
		this.lastRunTime = System.currentTimeMillis();
	}

	public int getExecutionCount() {
		return executionCount;
	}

	public void setExecutionCount(int executionCount) {
		this.executionCount = executionCount;
	}

	public long getLastRunTime() {
		return lastRunTime;
	}

	public void setLastRunTime(long lastRunTime) {
		this.lastRunTime = lastRunTime;
	}

	public String getLastRunInstance() {
		return lastRunInstance;
	}

	public void setLastRunInstance(String lastRunInstance) {
		this.lastRunInstance = lastRunInstance;
	}

	public String getModuleClass() {
		return moduleClass;
	}

	public void setModuleClass(String moduleClass) {
		this.moduleClass = moduleClass;
	}

	public String getSourceModuleClass() {
		return sourceModuleClass;
	}

	public void setSourceModuleClass(String sourceModuleClass) {
		this.sourceModuleClass = sourceModuleClass;
	}

	public double getPluginVersion() {
		return pluginVersion;
	}

	public void setPluginVersion(double pluginVersion) {
		this.pluginVersion = pluginVersion;
	}

	public double getSourcePluginVersion() {
		return sourcePluginVersion;
	}

	public void setSourcePluginVersion(double sourcePluginVersion) {
		this.sourcePluginVersion = sourcePluginVersion;
	}

	@JsonIgnore
	public String getCurrentLocation() {
		return currentLocation;
	}

	@JsonIgnore
	public void setCurrentLocation(String currentLocation) {
		this.currentLocation = currentLocation;
	}

	@JsonIgnore
	public String getCurrentInstance() {
		return currentInstance;
	}

	@JsonIgnore
	public void setCurrentInstance(String currentInstance) {
		this.currentInstance = currentInstance;
	}
	
	@JsonIgnore
	public void broadcast() throws JsonProcessingException {
		broadcast(LOCATION_ANYWHERE);
	}
	
	@JsonIgnore
	public void broadcast(String location) {
		x2call(Void.class, "broadcast", location, this);
	}
	
	@JsonIgnore
	public void send(String location) {
		x2call(Void.class, "send", location, this);
	}
	
	public static String getInstanceID() {
		return x2call(String.class,"getInstance");
	}
	
	public static String getInstanceQueueUrl() {
		return x2call(String.class,"getInstanceQueueUrl");
	}
	
	private static <T extends Object> T x2call(Class<T> returnType,String method,Object... parameters) {
		try {
			Class binding = Class.forName("com.cloudreach.connect.x2.internal.model.X2Message");
			Class[] paramList = (parameters == null || parameters.length == 0 ? new Class[] {} : new Class[parameters.length]);
			if(parameters != null) {
				for(int i = 0; i < paramList.length; i++) {
					paramList[i] = parameters[i].getClass();
				}
			}
			Method m = binding.getMethod(method, paramList);
			if(m != null) {
				return returnType.cast(m.invoke(binding, parameters));
			} else {
				Logger.getLogger("CC").log(Level.SEVERE, "[%s] METHOD DOES NOT EXISTING IN BINDING CLASS", new Object[]{method});
			}
		}catch(Throwable ex) {
			Logger.getLogger("CC").log(Level.SEVERE, String.format("[%s] - FATAL ERROR: {%s}", new Object[]{method, CCError.toString(ex)}));
		}
		
		return null;
	}
}
