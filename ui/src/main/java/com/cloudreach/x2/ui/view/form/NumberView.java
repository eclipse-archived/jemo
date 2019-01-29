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

import com.cloudreach.x2.ui.view.FormComponentView;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * this view will describe a control which will allow the user to input numbers
 * 
 * @author christopher stura
 */
public class NumberView extends FormComponentView<NumberView> {
	
	private double value = 0;
	
	public NumberView() {}
	
	public NumberView(String name) {
		super(name);
	}

	@JsonProperty(value = "value")
	public double getValue() {
		return value;
	}

	public NumberView setValue(double value) {
		this.value = value;
		return this;
	}

	@Override
	public NumberView setValue(String value) {
		if(value != null && !value.trim().isEmpty()) {
			try {
				this.value = new Double(value).doubleValue();
			}catch(NumberFormatException numFmtEx) {
				return super.setValue(value);
			}
		}
		return super.setValue(value); //To change body of generated methods, choose Tools | Templates.
	}
	
	
}
