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

import com.cloudreach.x2.ui.ManagedProducer;
import com.cloudreach.x2.ui.view.form.FormSectionView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * this is a simple container view that only has a size specification
 * 
 * @author christopher stura
 */
public class AreaView extends View {
	private String id = UUID.randomUUID().toString();
	private List<View> components = new ArrayList<>();
	private int size = 12;
	private String style = null;
	
	public AreaView() {}
	
	public AreaView(int size) {
		this.size = size;
	}

	public int getSize() {
		return size;
	}

	public AreaView setSize(int size) {
		this.size = size;
		
		return this;
	}
	
	public List<View> getComponents() {
		return components;
	}

	public void setComponents(List<View> components) {
		this.components = components;
	}
	
	@JsonIgnore
	public AreaView addComponentIf(View component,boolean condition) {
		if(condition) {
			addComponent(component);
		}
		
		return this;
	}
	
	@JsonIgnore
	public AreaView addComponent(View component) {
		getComponents().add(component);
		if(component.getContainerId() == null) {
			component.setContainerId(id); //if no container has been defined make us the container
		}
		if(component instanceof ButtonView) {
			ButtonView btnView = ButtonView.class.cast(component);
			if(btnView.getTarget() == null) {
				btnView.setTarget(getId());
			}
		}
		return this;
	}
	
	@JsonIgnore
	public AreaView addComponents(int quantity,ManagedProducer<View,Integer> producer) throws Throwable {
		for(int i = 1; i <= quantity; i++) {
			addComponent(producer.produce(i));
		}
		
		return this;
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getStyle() {
		return style;
	}

	public AreaView setStyle(String style) {
		this.style = style;
		return this;
	}
}
