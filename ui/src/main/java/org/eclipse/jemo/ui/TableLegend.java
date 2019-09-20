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
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * this class is the data that will help to identify how data will be identified within it's rows.
 * these legends will appear as badges next to the list of actions and will provide an icon based
 * description with an associated color which should describe the row data being in a particular state.
 *
 * @author christopher stura
 */
public class TableLegend extends View {
    private ICON icon = null;
    private String color = null;
    private String title = null;
    private String legendClass = null;
    private String action = null;
    private String target = null;

    public TableLegend() {
    }

    public TableLegend(ICON icon) {
        this(icon, null);
    }

    public TableLegend(ICON icon, String color) {
        this(icon, color, null);
    }

    public TableLegend(ICON icon, String color, String title) {
        this.icon = icon;
        this.color = color;
        this.title = title;
    }

    @JsonProperty(value = "class")
    public String getLegendClass() {
        return legendClass;
    }

    public TableLegend withClass(String className) {
        this.legendClass = className;
        return this;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    @JsonIgnore
    public TableLegend withAction(String target, Class<? extends Button> action) {
        this.action = action.getName();
        this.target = target;
        return this;
    }

    @JsonIgnore
    public ICON getIcon() {
        return icon;
    }

    @JsonIgnore
    public void setIcon(ICON icon) {
        this.icon = icon;
    }

    @JsonProperty(value = "icon")
    public String getIconStr() {
        return this.icon != null ? this.icon.toString() : null;
    }

    @JsonProperty(value = "icon")
    public void setIconStr(String iconStr) {
        for (ICON icn : ICON.values()) {
            if (icn.toString().equals(iconStr)) {
                setIcon(icn);
                break;
            }
        }
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    @JsonProperty(value = "title")
    public String getTitle() {
        return title;
    }

    @JsonProperty(value = "title")
    public void setTitle(String title) {
        this.title = title;
    }

    @JsonIgnore
    public TableLegend withIcon(ICON icon) {
        setIcon(icon);
        return this;
    }

    @JsonIgnore
    public TableLegend withColor(String color) {
        setColor(color);
        return this;
    }

    @JsonIgnore
    public TableLegend withTitle(String title) {
        setTitle(title);
        return this;
    }

    @JsonIgnore
    public TableLegend setAttribute(String key, String value) {
        return super.setAttribute(TableLegend.class, key, value);
    }
}
