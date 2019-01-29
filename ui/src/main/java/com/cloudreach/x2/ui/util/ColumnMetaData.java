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
package com.cloudreach.x2.ui.util;

import java.sql.ResultSetMetaData;
import java.sql.SQLException;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class ColumnMetaData {

	private String name = null;
	private int type = 0;

	public ColumnMetaData(ResultSetMetaData metaData, int columnPos) throws SQLException {
		this.name = metaData.getColumnName(columnPos);
		this.type = metaData.getColumnType(columnPos);
	}

	public String getName() {
		return name;
	}

	public int getType() {
		return type;
	}
}
