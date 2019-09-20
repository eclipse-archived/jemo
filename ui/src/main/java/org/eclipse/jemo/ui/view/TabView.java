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
package org.eclipse.jemo.ui.view;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * this view is essentially just a split view where multiple area's are handled under the badge of different tabs.
 *
 * @author christopher stura
 */
public class TabView extends AreaView {

    public static class Tab {
        private String key;
        private String title;
        private View view;
        private boolean active;

        public Tab(String key, String title, View view) {
            this.key = key;
            this.title = title;
            this.view = view;
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public View getView() {
            return view;
        }

        public boolean isActive() {
            return active;
        }

        public void setActive(boolean active) {
            this.active = active;
        }

    }

    private Map<String, Tab> tabs = new LinkedHashMap<>();

    public TabView() {
    }

    public TabView(int size) {
        super(size);
    }

    @JsonIgnore
    public TabView addTab(String title, View tabBody) {
        Tab t = new Tab("tab" + (tabs.size() + 1), title, tabBody);
        if (tabs.isEmpty()) {
            t.setActive(true);
        }
        tabs.put(t.getKey(), t);

        return this;
    }

    @JsonProperty(value = "tabs")
    public Collection<Tab> getTabs() {
        return tabs.values();
    }

    public TabView setActive(String activeTabTitle) {
        if (activeTabTitle != null) {
            Tab tab = tabs.values().parallelStream().filter(t -> t.getTitle().equalsIgnoreCase(activeTabTitle)).findAny().orElse(null);
            if (tab != null) {
                tabs.values().forEach(t -> t.setActive(false));
                tab.setActive(true);
            }
        }

        return this;
    }
}
