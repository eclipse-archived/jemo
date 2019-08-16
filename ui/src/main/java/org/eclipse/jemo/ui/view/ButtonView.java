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
import org.eclipse.jemo.ui.Button;
import org.eclipse.jemo.ui.ICON;
import org.eclipse.jemo.ui.TableRow;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

/**
 * this class will define the view for a button.
 *
 * @author christopher stura
 */
public class ButtonView extends View {

    public enum EXECUTION_MODE {
        CLICK, TIMEOUT, INTERVAL
    }

    private String logName = null;
    private String formId = null;
    private ICON icon = null;
    private String title = null;
    private String description = null;
    private String target = null; //the id of the UI component where the returning view would be drawn.
    private Class<? extends Button> action = null;
    private String additionalClass = null;
    private String style = null;
    private TableRow dataRow = null;
    private boolean hidden = false;
    private EXECUTION_MODE mode = EXECUTION_MODE.CLICK;
    private long execution_interval = -1;


    /**
     * this constructor serves only to deserialise from inbound json
     **/
    public ButtonView() {
    }

    public ButtonView(String title, String target, Class<? extends Button> action) {
        this.title = title;
        this.target = target;
        this.action = action;
    }

    public ButtonView(String title, Class<? extends Button> action) {
        this.title = title;
        this.action = action;
    }

    public String getFormId() {
        return formId;
    }

    public ButtonView setFormId(String formId) {
        this.formId = formId;
        return this;
    }

    @JsonIgnore
    public ICON getIcon() {
        return icon;
    }

    public ButtonView setIcon(ICON icon) {
        this.icon = icon;

        return this;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public String getTarget() {
        return target;
    }

    public void setTarget(String target) {
        this.target = target;
    }

    public String getLogName() {
        return logName;
    }

    public ButtonView setLogName(String logName) {
        this.logName = logName;
        return this;
    }

    @JsonIgnore
    public Class<? extends Button> getAction() {
        return action;
    }

    @JsonProperty(value = "action")
    public String getActionStr() {
        return getAction() != null ? getAction().getName() : null;
    }

    @JsonProperty(value = "icon")
    public String getIconStr() {
        return getIcon() == null ? null : getIcon().toString();
    }

    @JsonProperty(value = "description")
    public String getDescription() {
        return description;
    }

    @JsonIgnore
    public ButtonView setDescription(String description) {
        this.description = description;

        return this;
    }

    @JsonIgnore
    public TableRow getRow() {
        if (dataRow == null) {
            try {
                dataRow = getAttributeAsObject(TableRow.class, "row");
            } catch (IOException ioEx) {
                return null;
            }
        }

        return dataRow;
    }

    /**
     * this method will remove any associated row data from the view.
     *
     * @return a reference to the current object on which the operation was called
     */
    @JsonIgnore
    public ButtonView clearRow() {
        if (getAttributes().containsKey("row")) {
            getAttributes().remove("row");
        }

        return this;
    }

    @JsonIgnore
    public ButtonView setRow(TableRow row) throws JsonProcessingException {
        dataRow = row;
        getAttributes().put("row", Util.toJSON(row));

        return this;
    }

    @JsonIgnore
    public ButtonView setAttribute(String key, String value) {
        return super.setAttribute(getClass(), key, value);
    }

    @JsonProperty(value = "extendedclass")
    public String getAdditionalClass() {
        return additionalClass;
    }

    @JsonProperty(value = "extendedclass")
    public ButtonView setAdditionalClass(String additionalClass) {
        this.additionalClass = additionalClass;

        return this;
    }

    @JsonProperty(value = "style")
    public String getStyle() {
        return style;
    }

    @JsonProperty(value = "style")
    public ButtonView setStyle(String style) {
        this.style = style;

        return this;
    }

    @JsonIgnore
    public ButtonView hide() {
        setHidden(true);
        return this;
    }

    @JsonProperty(value = "hidden")
    public boolean isHidden() {
        return this.hidden;
    }

    @JsonProperty(value = "hidden")
    public ButtonView setHidden(boolean hidden) {
        this.hidden = hidden;
        return this;
    }

    @JsonIgnore
    public ButtonView runAfter(TimeUnit unit, long period) {
        mode = EXECUTION_MODE.TIMEOUT;
        execution_interval = unit.toMillis(period);
        return this;
    }

    @JsonIgnore
    public ButtonView runEvery(TimeUnit unit, long period) {
        mode = EXECUTION_MODE.INTERVAL;
        execution_interval = unit.toMillis(period);
        return this;
    }

    @JsonProperty(value = "execution_mode")
    public String getExecutionMode() {
        return mode.name();
    }

    @JsonProperty(value = "execution_interval")
    public long getExecutionInterval() {
        return execution_interval;
    }
}
