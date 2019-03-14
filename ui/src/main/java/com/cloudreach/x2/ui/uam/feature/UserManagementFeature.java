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
package com.cloudreach.x2.ui.uam.feature;

import com.cloudreach.x2.ui.ApplicationFeature;
import com.cloudreach.x2.ui.ICON;
import com.cloudreach.x2.ui.uam.panel.UserManagementPanel;
import static com.cloudreach.x2.ui.util.i18n.*;

/**
 * this class represents the feature that can be included in jemo-ui applications
 * to provide management for users in the system.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class UserManagementFeature extends ApplicationFeature {
	
	public static UserManagementFeature FEATURE = new UserManagementFeature();
	
	public UserManagementFeature() {
		super(ICON.users, USER_ACCESS_MANAGEMENT.getString("app.users.title"), UserManagementPanel.class);
	}
}
