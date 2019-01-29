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
package com.cloudreach.x2.ui.view.form;

import com.cloudreach.x2.ui.util.Util;
import com.cloudreach.x2.ui.view.FormComponentView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * this view will describe yes no values with no tri-state support
 * 
 * @author christopher stura
 */
public class BooleanView extends FormComponentView<BooleanView> {
	
	private boolean value = false;
	
	public BooleanView() {}
	
	public BooleanView(String name) {
		super(name);
	}

	@JsonProperty(value = "value")
	public boolean isValue() {
		return value;
	}

	@JsonProperty(value = "value")
	public BooleanView setValue(boolean value) {
		this.value = value;
		
		return this;
	}

	@Override
	@JsonIgnore
	public BooleanView setValue(String value) {
		switch(Util.S(value).toLowerCase()) {
			case "f":
			case "false":
			case "no":
				setValue(false);
				break;
			case "t":
			case "true":
			case "yes":
				setValue(true);
				break;
		}
		return super.setValue(value); //To change body of generated methods, choose Tools | Templates.
	}
	
}
