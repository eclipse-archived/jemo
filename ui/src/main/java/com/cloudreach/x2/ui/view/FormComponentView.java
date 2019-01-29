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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * this is a wrapper class that will identify all form components
 * 
 * @author christopher stura
 * @param <T> the type of component this component describes
 */
public abstract class FormComponentView<T extends FormComponentView> extends View {
	private String title = null;
	private String name = null;

	protected FormComponentView() {}
	
	protected FormComponentView(String name) {
		this.name = name;
	}
	
	@JsonProperty(value = "title")
	public String getTitle() {
		return title;
	}

	@JsonIgnore
	public T setTitle(String title) {
		this.title = title;
		
		return (T)this;
	}

	@JsonProperty(value = "name")
	public String getName() {
		return name;
	}

	@JsonIgnore
	public T setName(String name) {
		this.name = name;
		
		return (T)this;
	}
	
	@JsonIgnore
	public T setValue(String value) { return (T)this; }
}
