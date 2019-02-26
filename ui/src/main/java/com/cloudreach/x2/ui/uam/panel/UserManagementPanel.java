/*
********************************************************************************
* Copyright (c) 26th February 2019 Cloudreach Limited Europe
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
package com.cloudreach.x2.ui.uam.panel;

import com.cloudreach.x2.ui.ApplicationFeature;
import com.cloudreach.x2.ui.Panel;
import com.cloudreach.x2.ui.view.PanelView;

/**
 * this class will implement the user management panel interface.
 * 
 * the management panel will not directly access any of the data sources but will instead 
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class UserManagementPanel extends Panel {

	@Override
	public PanelView onCreate(ApplicationFeature feature) throws Throwable {
		//the initial user management panel should show a list of the users in the system. By default users have the following properties
		//1. they can be active or inactive
		//2. they can be part of one or more groups.
		//3. they can have metadata associated to them. (key/value) data
		//4. they will have an unique id, a first name, a last name, an e-mail
		//5. we will track system metadata including:
		//	- the last time the logged in.
		//	- a list of the services their security profile allows them to access
		//	- a list of the services they have accessed including the last time they were accessed.
		return null;
	}
	
}
