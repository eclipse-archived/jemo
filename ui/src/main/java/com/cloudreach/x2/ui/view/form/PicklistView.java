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

import com.cloudreach.x2.ui.KeyValue;
import com.cloudreach.x2.ui.view.FormComponentView;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author christopher stura
 */
public class PicklistView<T extends PicklistView> extends FormComponentView<T> {
	//here I have to use a custom json object map and save the values to a list using a generic key pair value item
	private List<KeyValue<String,String>> values = new ArrayList<>();
	private String selectedValue = null;
	
	public PicklistView() {}
	
	public PicklistView(String name,Map<String,String> values) {
		super(name);
		for(Map.Entry<String,String> e : values.entrySet()) {
			this.values.add(new KeyValue<>(e.getKey(),e.getValue()));
		}
	}
	
	public PicklistView(String name,String[]... values) {
		super(name);
		for(String[] value : values) {
			this.values.add(new KeyValue<>(value[0],value[1]));
		}
	}
	
	public T setSelected(String valueKey) {
		this.selectedValue = valueKey;
		return (T)this;
	}

	@JsonProperty(value = "selected")
	public String getSelectedValue() {
		return selectedValue;
	}
	
	public List<KeyValue<String,String>> getValues() {
		return values;
	}

	@Override
	public T setValue(String value) {
		return setSelected(value);
	}
	
	
}
