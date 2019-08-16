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
package org.eclipse.jemo.utilities;

import java.util.HashMap;
import java.util.Map;

/**
 * this class will make it easy to process scalar sql results
 * queries which return a single line.
 * 
 * @author christopher stura
 */
public class SqlResult {
	private Map<String,Object> data = new HashMap<>();
	
	public SqlResult(Map<String,Object> data) {
		this.data.putAll(data);
	}
	
	public int getInt(String columnName) {
		Object dataValue = data.get(columnName);
		if(dataValue instanceof Integer)
			return (Integer)dataValue;
		else if(dataValue instanceof Long)
			return Long.class.cast(dataValue).intValue();
		else if(dataValue instanceof Double)
			return Double.class.cast(dataValue).intValue();
		else if(dataValue != null)
			return new Double(dataValue.toString()).intValue();
		
		return 0;
	}
	
	public long getLong(String columnName) {
		return (Long)data.get(columnName);
	}
	
	public String getString(String columnName) {
		return (String)data.get(columnName);
	}
}
