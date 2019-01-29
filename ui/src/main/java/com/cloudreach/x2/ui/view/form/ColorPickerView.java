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
 * this visual component will allow a user to pick a color in a form.
 * 
 * @author christopher stura
 */
public class ColorPickerView extends FormComponentView<ColorPickerView> {
	
	private String value = null;
	
	public ColorPickerView() { super(); }
	
	public ColorPickerView(String name,String title) {
		super(name);
		setTitle(title);
	}

	@Override
	@JsonProperty(value = "value")
	public ColorPickerView setValue(String value) {
		this.value = value;
		return super.setValue(value); //To change body of generated methods, choose Tools | Templates.
	}

	@JsonProperty(value = "value")
	public String getValue() {
		return value;
	}
}
