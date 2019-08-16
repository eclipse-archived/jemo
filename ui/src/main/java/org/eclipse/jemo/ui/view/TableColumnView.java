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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author christopher stura
 */
public class TableColumnView extends View {
    public static final TableSectionView TABLE_SECTION = new TableSectionView().setKey("TABLE").setTitle("Important Information").setOrder(0);
    public static final TableSectionView DATA_SECTION = new TableSectionView().setKey("SECTION").setTitle("Other Data").setOrder(1);

    private boolean visible = true;
    private String key = null;
    private String name = null;
    private TableSectionView section = null;
    private int order = 0;
    private int size = 0;

    public TableColumnView() {
        this.section = TABLE_SECTION.copy();
    }

    public TableColumnView(String key, String name) {
        this.name = name;
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    @JsonProperty(value = "visible")
    public boolean isVisible() {
        return visible;
    }

    public TableColumnView setVisible(boolean visible) {
        this.visible = visible;

        return this;
    }

    @JsonProperty(value = "name")
    public String getName() {
        return name;
    }

    public TableColumnView setName(String name) {
        this.name = name;

        return this;
    }

    public TableSectionView getSection() {
        return section;
    }

    public TableColumnView setSection(TableSectionView section) {
        this.section = section;
        return this;
    }

    public int getOrder() {
        return order;
    }

    public TableColumnView setOrder(int order) {
        this.order = order;
        return this;
    }

    public int getSize() {
        return size;
    }

    public TableColumnView setSize(int size) {
        this.size = size;

        return this;
    }
}
