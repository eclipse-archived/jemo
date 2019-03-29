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

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Models the information that the UI needs to present to users that don't have the admin user (a.k.a. terraform user) credentials.
 * It provides a list of steps to create the admin user along with a text description.
 * @author Yannis Theocharis
 */
public class AdminUserCreationInstructions {

    @JsonProperty
    private final String description;

    @JsonProperty
    private final List<String> steps;

    public AdminUserCreationInstructions(String description, List<String> steps) {
        this.description = description;
        this.steps = steps;
    }

}
