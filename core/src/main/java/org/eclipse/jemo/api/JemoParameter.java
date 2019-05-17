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
    CLOUD("ECLIPSE_JEMO_CLOUD"),
    LOCATION("ECLIPSE_JEMO_LOCATION"),
    HTTPS_PORT("ECLIPSE_JEMO_HTTPS_PORT"),
    HTTP_PORT("ECLIPSE_JEMO_HTTP_PORT"),
    MODULE_WHITELIST("ECLIPSE_JEMO_MODULE_WHITELIST"),
    MODULE_BLACKLIST("ECLIPSE_JEMO_MODULE_BLACKLIST"),
    QUEUE_POLL_TIME("ECLIPSE_JEMO_QUEUE_POLLTIME"),
    LOCATION_TYPE("ECLIPSE_JEMO_LOCATION_TYPE"),
    LOG_LOCAL("ECLIPSE_JEMO_LOG_LOCAL"),
    LOG_OUTPUT("ECLIPSE_JEMO_LOG_OUTPUT"),
    LOG_LEVEL("ECLIPSE_JEMO_LOG_LEVEL");

    private final String label;

    JemoParameter(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
