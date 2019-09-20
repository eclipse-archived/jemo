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

import org.eclipse.jemo.ui.util.ManagedConsumer;

/**
 * this is the base exception which will be used to report any errors throughout the implementation
 * of the User Access Management platform component.
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class UserAccessManagementException extends Exception {

    public UserAccessManagementException(String reason, Throwable cause) {
        super(reason, cause);
    }

    protected UserAccessManagementException(Throwable cause) {
        super(cause);
    }

    public static void wrap(ManagedConsumer<Void> consumer) throws UserAccessManagementException {
        try {
            consumer.accept(null);
        } catch (Throwable ex) {
            throw new UserAccessManagementException(ex);
        }
    }
}
