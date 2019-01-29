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
package com.cloudreach.x2.ui;

import com.cloudreach.x2.ui.view.FormComponentView;
import com.cloudreach.x2.ui.view.form.CodeView;
import com.cloudreach.x2.ui.view.form.PicklistView;
import com.cloudreach.x2.ui.view.form.TextView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.ArrayList;
import java.util.List;

/**
 * this represents the data model for a table row.
 * 
 * @author christopher stura
 */
public class TableRow extends Component {
	private List<TableCell> cells = new ArrayList<>();
	private List<TableLegend> legends = new ArrayList<>();
	private List<String> excludedActions = new ArrayList<>();

	public List<TableCell> getCells() {
		return cells;
	}

	public void setCells(List<TableCell> cells) {
		this.cells = cells;
	}

	public List<TableLegend> getLegends() {
		return legends;
	}

	public void setLegends(List<TableLegend> legend) {
		this.legends.clear();
		this.legends.addAll(legend);
	}
	
	@JsonIgnore
	public TableRow addLegend(TableLegend legend) {
		getLegends().add(legend);
		
		return this;
	}
	
	@JsonIgnore
	public TableRow addCell(String name,String value) {
		return addCell(name,value,null);
	}
	
	@JsonIgnore
	public TableRow addCell(String name,String value,FormComponentView view) {
		TableCell cell = new TableCell();
		cell.setName(name);
		cell.setValue(value);
		if(view != null) {
			if(view instanceof TextView) {
				((TextView) view).setValue(value);
			} else if(view instanceof CodeView) {
				((CodeView) view).setValue(value);
			} else if(view instanceof PicklistView) {
				((PicklistView) view).setSelected(value);
			}
			cell.setView(view);
		}
		getCells().add(cell);
		
		return this;
	}
	
	public TableCell getCell(String name) {
		return cells.stream().filter((c) -> { return c.getName().equalsIgnoreCase(name); }).findFirst().orElse(null);
	}
	
	@JsonIgnore
	public String getCellValue(String name) {
		TableCell cell = getCell(name);
		if(cell != null) {
			return cell.getValue();
		}
		
		return null;
	}

	@JsonProperty(value = "exclude-actions")
	public List<String> getExcludedActions() {
		return excludedActions;
	}

	@JsonProperty(value = "exclude-actions")
	public void setExcludedActions(List<String> excludedActions) {
		this.excludedActions = excludedActions;
	}
}
