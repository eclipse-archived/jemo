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

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 *
 * @author christopher stura
 */
public class MultiselectPicklistView extends PicklistView<MultiselectPicklistView> {
	
	private List<String> selectedValues = new ArrayList<>();
	private int heightInPixels = 150;
	
	public MultiselectPicklistView() {}
	
	public MultiselectPicklistView(String name, Map<String, String> values) {
		super(name, values);
	}

	@Override
	@JsonIgnore
	public String getSelectedValue() {
		if(selectedValues.isEmpty()) {
			return null;
		} else {
			return selectedValues.get(0);
		}
	}

	@Override
	public MultiselectPicklistView setSelected(String valueKey) {
		selectedValues.clear();
		selectedValues.add(valueKey);
		return this;
	}
	
	@JsonIgnore
	public MultiselectPicklistView withSelected(String... valueKey) {
		selectedValues.clear();
		if(valueKey != null) {
			selectedValues.addAll(Arrays.asList(valueKey));
		}
		
		return this;
 	}

	@JsonProperty(value = "selected")
	public List<String> getSelectedValues() {
		return selectedValues;
	}

	@JsonProperty(value = "height")
	public int getHeightInPixels() {
		return heightInPixels;
	}

	@JsonProperty(value = "height")
	public MultiselectPicklistView setHeightInPixels(int heightInPixels) {
		this.heightInPixels = heightInPixels;
		
		return this;
	}
	
	
}
