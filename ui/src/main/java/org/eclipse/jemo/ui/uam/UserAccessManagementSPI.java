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
package org.eclipse.jemo.ui.uam;

import org.eclipse.jemo.ui.UserData;
import org.eclipse.jemo.ui.util.DataFilter;
import org.eclipse.jemo.ui.util.DataSortOrder;

import java.util.Set;

/**
 * the purpose of this interface is to provide a definition for how a data provider
 * should produce the necessary information for the user access management interface
 * as well as provide SDK type access to classes which want to make use of the UAM service
 * within their applications but may need to interact with the data model at some level.
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public interface UserAccessManagementSPI {
    Set<UserData> listUsers(Set<DataFilter> filters, Set<DataSortOrder> sortOrder, int offset, int limit) throws UserAccessManagementException;

    UserAccessManagementSPI addUser(UserData user) throws UserAccessManagementException;
}
