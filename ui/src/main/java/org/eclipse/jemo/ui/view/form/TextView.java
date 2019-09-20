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
package org.eclipse.jemo.ui.view.form;

import org.eclipse.jemo.ui.Button;
import org.eclipse.jemo.ui.view.FormComponentView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * @author christopher stura
 */
public class TextView extends FormComponentView<TextView> {

    private String value = null;
    private boolean submitOnEnter = false;
    private String formId = null;
    private String action = null;
    private String target = null;

    public TextView() {
    }

    public TextView(String name) {
        super(name);
    }

    @JsonProperty(value = "value")
    public String getValue() {
        return value;
    }

    @JsonIgnore
    public TextView setValue(String value) {
        this.value = value;

        return this;
    }

    @JsonProperty(value = "submit")
    public boolean isSubmitOnEnter() {
        return submitOnEnter;
    }

    @JsonProperty(value = "formId")
    public String getFormId() {
        return formId;
    }

    public String getAction() {
        return action;
    }

    public String getTarget() {
        return target;
    }

    @JsonIgnore
    public TextView submitOnEnter(Class<? extends Button> action, String target, String formId) {
        this.submitOnEnter = true;
        this.formId = formId;
        this.action = action.getName();
        this.target = target;
        return this;
    }
}
