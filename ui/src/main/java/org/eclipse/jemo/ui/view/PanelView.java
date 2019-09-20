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

import org.eclipse.jemo.ui.Button;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * @author christopher stura
 */
public class PanelView extends View {
    private String id = UUID.randomUUID().toString();
    private String formId = UUID.randomUUID().toString();
    private String title;
    private List<ButtonView> buttons = new ArrayList<>();
    private List<RowView> rows = new ArrayList<>();

    public PanelView(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getId() {
        return id;
    }

    @JsonProperty(value = "formid")
    public String getFormId() {
        return formId;
    }

    public List<RowView> getRows() {
        return rows;
    }

    public List<ButtonView> getButtons() {
        return buttons;
    }

    @JsonIgnore
    public RowView addRow() {
        RowView v = new RowView();
        v.setContainerId(getId());
        getRows().add(v);
        return v;
    }

    public <T extends View> T addButton(ButtonView button) {
        button.setFormId(formId);
        getButtons().add(button);

        return (T) this;
    }

    @JsonIgnore
    public ButtonView addButton(String title, Class<? extends Button> action) {
        return addButton(title, getId(), action);
    }

    @JsonIgnore
    public ButtonView addButton(String title, String target, Class<? extends Button> action) {
        ButtonView b = new ButtonView(title, target, action);
        b.setFormId(formId);
        getButtons().add(b);
        return b;
    }
}
