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
package org.eclipse.jemo.sys.internal;

import org.eclipse.jemo.internal.model.CloudProvider;
import org.eclipse.jemo.internal.model.SystemDBObject;
import org.eclipse.jemo.internal.model.CloudProvider;
import org.eclipse.jemo.internal.model.SystemDBObject;

import java.io.IOException;
import java.util.List;

/**
 * this is a wrapper class for access to system database information.
 * 
 * @author christopher stura
 */
public class SystemDB {
	
	public static boolean hasTable(String tableName) {
		return CloudProvider.getInstance().getRuntime().hasNoSQLTable(tableName);
	}
	
	public static boolean createTable(String tableName) {
		if(!hasTable(tableName)) {
			CloudProvider.getInstance().getRuntime().createNoSQLTable(tableName);
			
			return true;
		}
		
		return false;
	}
	
	public static <T extends Object> List<T> list(String tableName,Class<T> objectType) {
		//we assume that json values are stored in a specific table and therefore that extracted results are serialised.
		return CloudProvider.getInstance().getRuntime().listNoSQL(tableName, objectType);
	}
	
	public static <T extends Object> List<T> query(String tableName,Class<T> objectType,String... pkList) {
		return CloudProvider.getInstance().getRuntime().queryNoSQL(tableName, objectType, pkList);
	}
	
	public static <T extends Object> T get(String tableName,String id,Class<T> objectType) throws IOException {
		return CloudProvider.getInstance().getRuntime().getNoSQL(tableName, id, objectType);
	}
	
	public static void save(String tableName, SystemDBObject... data) {
		CloudProvider.getInstance().getRuntime().saveNoSQL(tableName, data);
	}
	
	public static void delete(String tableName,SystemDBObject... data) {
		CloudProvider.getInstance().getRuntime().deleteNoSQL(tableName, data);
	}
}
