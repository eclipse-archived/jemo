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
********************************************************************************
 */
package com.cloudreach.x2.ui.uam.spi;

import com.cloudreach.x2.ui.Application;
import com.cloudreach.x2.ui.UserData;
import com.cloudreach.x2.ui.uam.UserAccessManagementException;
import com.cloudreach.x2.ui.uam.UserAccessManagementSPI;
import com.cloudreach.x2.ui.util.DataFilter;
import com.cloudreach.x2.ui.util.DataSortOrder;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * this class represents a Jemo cloud agnostic spi implementation for the user access management system.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class JemoSPI implements UserAccessManagementSPI {
	private static final String USER_TABLE_SUFFIX = "users"; //tables on a cloud will be global so it is probably best to have a table for each application
	
	private final Application app;
	private final String USER_TABLE;
	private volatile AtomicBoolean initialized = new AtomicBoolean(false);
	
	public JemoSPI(Application app) {
		this.app = app;
		this.USER_TABLE = app.getAppKey()+"_"+USER_TABLE_SUFFIX;
	}
	
	private synchronized void init() throws UserAccessManagementException {
		if(!initialized.getAndSet(true)) {
			try {
				if(!app.getRuntime().hasNoSQLTable(USER_TABLE)) {
					app.getRuntime().createNoSQLTable(USER_TABLE);
				}
			} catch(Throwable ex) {
				throw new UserAccessManagementException("SPI initialization failed", ex);
			}
		}
	}

	@Override
	public Set<UserData> listUsers(Set<DataFilter> filters, Set<DataSortOrder> sortOrder, int offset, int limit) throws UserAccessManagementException {
		throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
	}

	@Override
	public UserAccessManagementSPI addUser(UserData user) throws UserAccessManagementException {
		UserAccessManagementException.wrap((f) -> {
			init();
			app.getRuntime().saveNoSQL(USER_TABLE, user);
		});
		return this;
	}
	
}
