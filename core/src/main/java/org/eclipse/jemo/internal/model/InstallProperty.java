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
package org.eclipse.jemo.internal.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * @author Yannis Theocharis
 */
public class InstallProperty {
    @JsonProperty
    private String name;

    @JsonProperty
    private String description;

    @JsonProperty
    private String value;

    @JsonProperty
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private List<String> range;

    public String name() {
        return name;
    }

    public InstallProperty name(String name) {
        this.name = name;
        return this;
    }

    public String description() {
        return description;
    }

    public InstallProperty description(String description) {
        this.description = description;
        return this;
    }

    public String value() {
        return value;
    }

    public InstallProperty value(String value) {
        this.value = value;
        return this;
    }

    public List<String> range() {
        return range;
    }

    public InstallProperty range(List<String> range) {
        this.range = range;
        return this;
    }
}
