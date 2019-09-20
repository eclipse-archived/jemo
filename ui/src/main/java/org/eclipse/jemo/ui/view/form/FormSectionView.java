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

import org.eclipse.jemo.ui.ManagedProducer;
import org.eclipse.jemo.ui.view.FormComponentView;
import org.eclipse.jemo.ui.view.View;
import org.eclipse.jemo.ui.view.ViewImportance;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * this view will describe a section of a form with a title and a border around it
 *
 * @author christopher stura
 */
public class FormSectionView extends FormComponentView<FormSectionView> {

    private String footer = null;
    private int size = 0;
    private ViewImportance importance = ViewImportance.DEFAULT;
    private List<View> components = new ArrayList<>();

    public FormSectionView() {
    }

    public FormSectionView(String title) {
        super(null);
        setTitle(title);
    }

    public int getSize() {
        return size;
    }

    public FormSectionView setSize(int size) {
        this.size = size;
        return this;
    }

    public String getFooter() {
        return footer;
    }

    public FormSectionView setFooter(String footer) {
        this.footer = footer;
        return this;
    }

    @JsonIgnore
    public ViewImportance getImportance() {
        return importance;
    }

    @JsonIgnore
    public FormSectionView setImportance(ViewImportance importance) {
        this.importance = importance;
        return this;
    }

    @JsonProperty(value = "importance")
    public String getImportanceStr() {
        return this.importance.name().toLowerCase();
    }

    @JsonProperty(value = "importance")
    public void setImportanceStr(String importanceStr) {
        for (ViewImportance importance : ViewImportance.values()) {
            if (importance.name().equalsIgnoreCase(importanceStr)) {
                this.importance = importance;
                break;
            }
        }
    }

    @JsonProperty(value = "components")
    public List<View> getComponents() {
        return components;
    }

    @JsonIgnore
    public FormSectionView addComponent(View component) {
        getComponents().add(component);
        return this;
    }

    @JsonIgnore
    public FormSectionView addComponents(int quantity, ManagedProducer<View, Integer> producer) throws Throwable {
        for (int i = 1; i <= quantity; i++) {
            addComponent(producer.produce(i));
        }

        return this;
    }

    @JsonIgnore
    public FormSectionView addComponentFirst(View component) {
        getComponents().add(0, component);
        return this;
    }
}
