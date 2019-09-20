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

import org.eclipse.jemo.ui.view.View;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * this class will represent the JSON structure which will be used to build the features
 * of the top menu item.
 *
 * @author Christopher Stura 
 */
public class TopMenu extends View {
    private boolean showLogout = false;
    private String title = null;
    private final List<ApplicationFeature> items = new ArrayList<>();
    private final List<ApplicationFeature> messages = new ArrayList<>();
    private final List<ApplicationFeature> notifications = new ArrayList<>();

    @JsonIgnore
    public TopMenu withShowLogout(boolean showLogout) {
        this.showLogout = showLogout;
        return this;
    }

    public boolean isShowLogout() {
        return showLogout;
    }

    public List<ApplicationFeature> getItems() {
        return items;
    }

    public List<ApplicationFeature> getMessages() {
        return messages;
    }

    public List<ApplicationFeature> getNotifications() {
        return notifications;
    }

    public String getTitle() {
        return title;
    }

    @JsonIgnore
    public TopMenu withTitle(String title) {
        this.title = title;
        return this;
    }

    @JsonIgnore
    public ApplicationFeature addItem(String title) {
        ApplicationFeature feature = items.parallelStream().filter(af -> af.getTitle().equalsIgnoreCase(title)).findAny().orElse(null);
        if (feature == null) {
            feature = new ApplicationFeature(null, title);
            getItems().add(feature);
        }

        return feature;
    }
}
