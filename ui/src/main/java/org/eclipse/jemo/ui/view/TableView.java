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

import org.eclipse.jemo.ui.util.Util;
import org.eclipse.jemo.ui.Table;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * this class will represent the JSON structure that will be used to define a data table.
 *
 * @author christopher stura
 */
public class TableView extends View {

    private List<TableColumnView> columns = new ArrayList<>();
    private List<ButtonView> actions = new ArrayList<>();
    private Set<TableSectionView> sections = new TreeSet<>();
    private String controllerClass = null;
    private int pageSize = 25;
    private boolean allowLegends = false;
    private boolean showPageNumbers = true;
    private boolean allowSort = true;

    public TableView() {
    }

    public TableView(Class<? extends Table> tableController) {
        this.controllerClass = tableController.getName();
    }

    @Override
    public void setContainerId(String containerId) {
        super.setContainerId(containerId);
        for (ButtonView action : actions) {
            if (action.getTarget() == null) {
                action.setTarget(containerId);
            }
        }
    }

    @JsonProperty(value = "controller")
    public String getControllerClass() {
        return controllerClass;
    }

    @JsonProperty(value = "pagesize")
    public int getPageSize() {
        return pageSize;
    }

    @JsonProperty(value = "pagesize")
    public void setPageSizeJSON(int pageSize) {
        setPageSize(pageSize);
    }

    @JsonIgnore
    public TableView setPageSize(int pageSize) {
        this.pageSize = pageSize;
        return this;
    }

    public List<TableColumnView> getColumns() {
        return columns;
    }

    public void setColumns(List<TableColumnView> columns) {
        this.columns.clear();
        if (columns != null) {
            columns.forEach((c) -> addColumn(c));
        }
    }

    public TableView addColumn(TableColumnView column) {
        if (column != null) {
            getColumns().add(column);
            getSections().add(column.getSection());
        }
        return this;
    }

    public TableView addColumn(String key, String name, boolean visible, TableSectionView section, int order, int size) {
        TableSectionView altSectionView = section.copy();
        if (Util.S(altSectionView.getTitle()).trim().isEmpty()) {
            altSectionView.setTitle(altSectionView.getKey());
        }
        switch (section.getKey()) {
            case "TABLE":
                altSectionView = TableColumnView.TABLE_SECTION;
                break;
            case "SECTION":
                altSectionView = TableColumnView.DATA_SECTION;
                break;
        }
        if (Util.S(section.getTitle()).trim().isEmpty()) {
            section.setTitle(altSectionView.getTitle());
        }
        getColumns().add(new TableColumnView(key, name).setVisible(visible).setSection(section).setOrder(order).setSize(size));
        getSections().add(section); //populate the sections so we are sure we have a unique ordered list which we can then work with in our javascript.
        return this;
    }

    public TableView addColumn(String key, String name, boolean visible) {
        return addColumn(key, name, visible, 0);
    }

    public TableView addColumn(String key, String name, boolean visible, int size) {
        return addColumn(key, name, visible, (visible ? TableColumnView.TABLE_SECTION.copy() : TableColumnView.DATA_SECTION.copy()), getColumns().parallelStream().max(Comparator.comparing(TableColumnView::getOrder))
                .orElse(new TableColumnView().setOrder(0)).getOrder() + 1, size);
    }

    public List<ButtonView> getActions() {
        return actions;
    }

    @JsonIgnore
    public TableView setAttribute(String name, String value) {
        return setAttribute(TableView.class, name, value);
    }

    @JsonIgnore
    public TableView addAction(ButtonView button) {
        if (button.getTarget() == null) {
            button.setTarget(getContainerId());
        }
        getActions().add(button);

        return this;
    }

    public Set<TableSectionView> getSections() {
        return sections;
    }

    public void setSections(Set<TableSectionView> sections) {
        this.sections = sections;
    }

    @JsonProperty(value = "legends")
    public boolean isAllowLegends() {
        return allowLegends;
    }

    @JsonProperty(value = "legends")
    public void setAllowLegends(boolean allowLegends) {
        this.allowLegends = allowLegends;
    }

    @JsonProperty(value = "show_page_numbers")
    public boolean isShowPageNumbers() {
        return showPageNumbers;
    }

    @JsonIgnore
    public void setShowPageNumbers(boolean showPageNumbers) {
        this.showPageNumbers = showPageNumbers;
    }

    @JsonProperty(value = "allow_sort")
    public boolean isAllowSort() {
        return allowSort;
    }

    @JsonProperty(value = "allow_sort")
    public void setAllowSort(boolean allowSort) {
        this.allowSort = allowSort;
    }

    public TableView withAllowLegends(boolean allowLegends) {
        setAllowLegends(allowLegends);
        return this;
    }
}
