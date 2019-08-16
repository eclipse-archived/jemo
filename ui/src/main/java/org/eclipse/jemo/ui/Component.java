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
package org.eclipse.jemo.ui;

import java.util.Map;
import java.util.logging.Level;

/**
 * this is the basic component from which all components are derived.
 *
 * @author christopher stura
 */
public abstract class Component {
    private transient Application application = null;

    public Application getApplication() {
        if (application == null && this instanceof Application) {
            return (Application) this;
        }
        return application;
    }

    public void setApplication(Application application) {
        this.application = application;
    }

    public void info(String message, String... parameters) {
        log(Level.INFO, message, parameters);
    }

    public void log(Level logLevel, String message, String... parameters) {
        Application.moduleLog(logLevel, (getApplication() != null ? "[" + getApplication().getClass().getSimpleName() + "]" : "") + "[" + getClass().getSimpleName() + "]" + message, parameters);
    }

    protected Object handleEvent(String event, Map<String, Object> parameters, String payload) throws Throwable {
        throw new ApplicationException("[" + getClass().getName() + "][" + event + "] Unhanlded event PAYLOAD: " + payload);
    }
}
