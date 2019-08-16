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

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.jemo.internal.model.SystemDBObject;

import java.util.HashMap;
import java.util.Map;

/**
 * this class will contain basic information about the user as presented through
 * our oAuth providers. This information will be available to all backend processes
 * throughout the application through the getAuthenticatedUser method.
 *
 * @author christopher stura
 */
public class UserData implements SystemDBObject {
    private String id = null;
    private String username = null;
    private String firstName = null;
    private String lastName = null;
    private String message = null;
    private Map<String, Object> data = new HashMap<>();

    @Override
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonProperty(value = "userPrincipalName")
    public String getUsername() {
        return username;
    }

    @JsonProperty(value = "userPrincipalName")
    public void setUsername(String username) {
        this.username = username;
    }

    @JsonProperty(value = "givenName")
    public String getFirstName() {
        return firstName;
    }

    @JsonProperty(value = "givenName")
    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    @JsonProperty(value = "surname")
    public String getLastName() {
        return lastName;
    }

    @JsonProperty(value = "surname")
    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, Object> getData() {
        return data;
    }

    public void setData(Map<String, Object> data) {
        this.data = data;
    }
}
