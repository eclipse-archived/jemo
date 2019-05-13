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
package org.eclipse.jemo.api;

public enum JemoParameter {
    CLOUD("eclipse.jemo.cloud"),
    LOCATION("eclipse.jemo.location"),
    HTTPS_PORT("eclipse.jemo.https.port"),
    HTTP_PORT("eclipse.jemo.http.port"),
    MODULE_WHITELIST("eclipse.jemo.module.whitelist"),
    MODULE_BLACKLIST("eclipse.jemo.module.blacklist"),
    QUEUE_POLL_TIME("eclipse.jemo.queue.polltime"),
    LOCATION_TYPE("eclipse.jemo.location.type"),
    LOG_LOCAL("eclipse.jemo.log.local"),
    LOG_OUTPUT("eclipse.jemo.log.output"),
    LOG_LEVEL("eclipse.jemo.log.level");

    private final String label;

    JemoParameter(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
