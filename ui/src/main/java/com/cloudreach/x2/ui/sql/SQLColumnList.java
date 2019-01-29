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
package com.cloudreach.x2.ui.sql;

import com.cloudreach.x2.ui.view.TableColumnView;
import com.cloudreach.x2.ui.view.TableSectionView;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 *
 * @author christopher stura
 */
public class SQLColumnList {
	private List<TableColumnView> columns = new ArrayList<>();
	
	public List<TableColumnView> asList() {
		return this.columns;
	}
	
	public SQLColumnList addColumn(String key,String name,boolean visible,TableSectionView section,int order,int size) {
		if(!columns.stream().anyMatch(c -> c.getKey().equals(key))) {
			asList().add(new TableColumnView(key, name).setVisible(visible).setSection(section).setOrder(order).setSize(size));
		}
		return this;
	}
	
	public SQLColumnList addColumn(String key,String name,boolean visible) {
		return addColumn(key,name,visible,0);
	}
	
	public SQLColumnList addColumn(String key,String name,boolean visible,int size) {
		return addColumn(key, name, visible, (visible ? TableColumnView.TABLE_SECTION.copy() : TableColumnView.DATA_SECTION.copy()), asList().parallelStream().max(Comparator.comparing(TableColumnView::getOrder)).orElse(new TableColumnView().setOrder(0)).getOrder()+1,size);
	}
}
