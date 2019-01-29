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
package com.cloudreach.x2.ui.view;

import com.cloudreach.x2.ui.util.Util;
import com.cloudreach.x2.ui.Button;
import com.cloudreach.x2.ui.Component;
import com.cloudreach.x2.ui.EventHandler;
import com.cloudreach.x2.ui.events.Event;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author christopher stura
 */
public abstract class View {
	private String infoMessage = null;
	private String warningMessage = null;
	private String cls = getClass().getSimpleName();
	private Map<String,String> attributes = new HashMap<>();
	private String containerId = null;
	private List<Event> events = new ArrayList<>();
	private Map<String,EventHandler> handlers = new LinkedHashMap<>();

	@JsonProperty(value = "info_message")
	public String getInfoMessage() {
		return infoMessage;
	}

	@JsonProperty(value = "warn_message")
	public String getWarningMessage() {
		return warningMessage;
	}

	@JsonIgnore
	public <T extends View> T setInfoMessage(String infoMessage) {
		this.infoMessage = infoMessage;
		
		return (T)this;
	}

	@JsonIgnore
	public <T extends View> T setWarningMessage(String warningMessage) {
		this.warningMessage = warningMessage;
		
		return (T)this;
	}
	
	public String getContainerId() {
		return containerId;
	}

	public void setContainerId(String containerId) {
		this.containerId = containerId;
	}

	@JsonProperty(value = "class")
	public String getCls() {
		return cls;
	}

	public Map<String, String> getAttributes() {
		return attributes;
	}

	public void setAttributes(Map<String, String> attributes) {
		this.attributes = attributes;
	}
	
	@JsonIgnore
	public <T extends View> T setAttribute(Class<T> view,String name,String value) {
		if(value != null) {
			getAttributes().put(name, value);
		} else {
			getAttributes().remove(name);
		}
		return view.cast(this);
	}
	
	@JsonIgnore
	public <T extends Object> T getAttributeAsObject(Class<T> type,String name) throws IOException {
		String attrValue = getAttributes().get(name);
		if(attrValue != null) {
			if(type.isAssignableFrom(attrValue.getClass())) {
				return type.cast(attrValue);
			} else {
				return Util.fromJSON(type, attrValue);
			}
		}
		
		return null;
	}
	
	@JsonIgnore
	public <T extends Object> List<T> getAttributeAsObjectList(Class<T> type,String name) throws IOException {
		String attrValue = getAttributes().get(name);
		if(attrValue != null) {
			return Util.fromJSONArray(type, attrValue);
		}
		
		return null;
	}
	
	@JsonIgnore
	public String getAttributeAsString(String name) {
		return getAttributes().get(name);
	}
	
	public int getAttributeAsInteger(String name) {
		String strAttr = getAttributeAsString(name);
		if(strAttr != null && strAttr.matches("[0-9\\.\\,]+")) {
			return new Double(strAttr).intValue();
		}
		
		return 0;
	}

	@JsonProperty(value = "events")
	public List<Event> getEvents() {
		return events;
	}
	
	@JsonIgnore
	public <T extends View> T addEvent(Event e) {
		getEvents().add(e);
		
		return (T)this;
	}

	public Map<String, EventHandler> getHandlers() {
		return handlers;
	}
	
	public <T extends View> T on(String uiEvent,Class<? extends Button> handler) {
		return on(uiEvent,getContainerId(),handler);
	}
	
	public <T extends View> T on(String uiEvent,String target,Class<? extends Button> handler) {
		return on(uiEvent,target,null,handler);
	}
	
	public <T extends View> T on(String uiEvent,Class<? extends Button> handler,String formId) {
		return on(uiEvent,getContainerId(),formId,handler);
	}
	
	public <T extends View> T on(String uiEvent,String target,String formId,Class<? extends Button> handler) {
		//this will register and list for a specific javascript event against a view ui component
		//and launch the handler class registered as a component the handler will be sent the context of the view which
		//the handler is associated to as context.
		getHandlers().put(uiEvent, new EventHandler(target, formId, handler));
		return (T)this;
	}
	
	public <T extends View> T copyAttributesFrom(View v) {
		getAttributes().putAll(v.getAttributes());
		
		return (T)this;
	}
}
