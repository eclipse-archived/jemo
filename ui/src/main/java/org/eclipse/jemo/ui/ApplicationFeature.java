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

import org.eclipse.jemo.ui.panel.URLPanel;
import org.eclipse.jemo.ui.view.View;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * this will describe the features that make up an application.
 * <p>
 * features can be contained as well so that the resemble a menu structure.
 *
 * @author christopher stura
 */
public class ApplicationFeature extends View {
    private String id = UUID.randomUUID().toString();
    private String title = null;
    private String description = null;
    private Class<? extends Panel> backend = null;
    private ICON icon = null;
    private List<ApplicationFeature> subfeatures = new ArrayList<>();
    private Class<? extends Button> action = null;
    private String script = null;
    private boolean anchorUserAccessManagement = false;

    public ApplicationFeature() {
    }

    public ApplicationFeature(ICON icon, String title) {
        this.title = title;
        this.icon = icon;
    }

    public ApplicationFeature(ICON icon, String title, Class<? extends Panel> backend) {
        this(icon, title);
        this.backend = backend;
    }

    public ApplicationFeature(Class<? extends Button> action, ICON icon, String title) {
        this(icon, title);
        this.action = action;
    }

    public ApplicationFeature(ICON icon, String title, String javascriptAction) {
        this(icon, title);
        this.script = javascriptAction;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    public String getTarget() {
        if (getScript() != null) {
            return "javascript:" + getScript();
        } else {
            return Application.MAIN_APPLICATION_AREA;
        }
    }

    @JsonIgnore
    public Class<? extends Button> getAction() {
        return action;
    }

    @JsonIgnore
    public ApplicationFeature setAction(Class<? extends Button> action) {
        this.action = action;
        return this;
    }

    @JsonProperty(value = "action")
    public String getActionStr() {
        return getAction() != null ? getAction().getName() : null;
    }

    @JsonProperty(value = "action")
    public ApplicationFeature setActionStr(String actionClass) {
        try {
            setAction((Class<Button>) Class.forName(actionClass));
        } catch (Exception ex) {
        }

        return this;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @JsonIgnore
    public Class<? extends Panel> getBackend() {
        return backend;
    }

    @JsonIgnore
    public void setBackend(Class<? extends Panel> backend) {
        this.backend = backend;
    }

    @JsonProperty(value = "backend")
    public String getBackendClass() {
        return getBackend() != null ? getBackend().getName() : null;
    }

    @JsonProperty(value = "backend")
    public void setBackendClass(String className) {
        try {
            setBackend((Class<Panel>) Class.forName(className));
        } catch (Exception ex) {
        }
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

    public List<ApplicationFeature> getSubfeatures() {
        return subfeatures;
    }

    public void setSubfeatures(List<ApplicationFeature> subfeatures) {
        this.subfeatures = subfeatures;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    @JsonIgnore
    public ApplicationFeature addFeature(ApplicationFeature feature) {
        getSubfeatures().add(feature);
        return this;
    }

    @JsonIgnore
    public ApplicationFeature addFeature(ICON icon, String title) {
        addFeature(new ApplicationFeature(icon, title));
        return this;
    }

    @JsonIgnore
    public ApplicationFeature addFeature(ICON icon, String title, String externalUrl) {
        if (!externalUrl.startsWith("javascript:")) {
            addFeature(URLPanel.buildFeature(icon, title, externalUrl));
        } else {
            addFeature(new ApplicationFeature(icon, title, externalUrl.substring("javascript:".length())));
        }
        return this;
    }

    @JsonIgnore
    public boolean isAnchorUserAccessManagement() {
        return anchorUserAccessManagement;
    }

    /**
     * by setting this to true the system will automatically add user access management applications to this
     * feature.
     *
     * @param anchorUserAccessManagement true if you want user access management applications put under this feature.
     * @return this object
     */
    @JsonIgnore
    public ApplicationFeature setAnchorUserAccessManagement(boolean anchorUserAccessManagement) {
        this.anchorUserAccessManagement = anchorUserAccessManagement;
        return this;
    }
}
