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
import org.eclipse.jemo.ui.ICON;
import org.eclipse.jemo.ui.POSITION;
import org.eclipse.jemo.ui.view.FormComponentView;

/**
 * this view describes a component which contains both an input text box and an adjacent button
 * which can preform some kind of action with the text that is contained within it.
 *
 * @author christopher stura
 */
public class InputButtonView extends FormComponentView<InputButtonView> {

    private String action = null;
    private String icon = null;
    private POSITION position = POSITION.RIGHT;
    private String target = null;
    private String formId = null;

    public InputButtonView() {
    }

    public InputButtonView(String name, ICON actionIcon, Class<? extends Button> action) {
        super(name);
        if (action != null) {
            this.action = action.getName();
        }
        this.icon = actionIcon.toString();
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public POSITION getPosition() {
        return position;
    }

    public InputButtonView setPosition(POSITION position) {
        this.position = position;

        return this;
    }

    public String getTarget() {
        return target;
    }

    public InputButtonView setTarget(String target) {
        this.target = target;

        return this;
    }

    public String getFormId() {
        return formId;
    }

    public InputButtonView setFormId(String formId) {
        this.formId = formId;

        return this;
    }


}
