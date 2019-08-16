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

import org.eclipse.jemo.ui.KeyValue;
import org.eclipse.jemo.ui.view.FormComponentView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * @author christopher stura
 */
public class CheckboxGroupView extends FormComponentView<CheckboxGroupView> {

    private List<KeyValue<String, String>> values = new ArrayList<>();
    private List<String> checked = new ArrayList<>();

    public CheckboxGroupView() {
    }

    public CheckboxGroupView(String name) {
        super(name);
    }

    public CheckboxGroupView(String name, Map<String, String> values) {
        super(name);

        values.forEach((k, v) -> this.values.add(new KeyValue<>(k, v)));
    }

    @JsonProperty(value = "values")
    public List<KeyValue<String, String>> getValues() {
        return values;
    }

    @JsonProperty(value = "values")
    public CheckboxGroupView setValues(List<KeyValue<String, String>> values) {
        this.values = values;

        return this;
    }

    @JsonProperty(value = "checked")
    public List<String> getChecked() {
        return checked;
    }

    @JsonIgnore
    public CheckboxGroupView addValue(String key, String value) {
        this.values.add(new KeyValue<>(key, value));
        return this;
    }

    @JsonIgnore
    public CheckboxGroupView setChecked(String key) {
        if (!checked.contains(key)) {
            checked.add(key);
        }

        return this;
    }

    @JsonIgnore
    public CheckboxGroupView setChecked(List<String> keyList) {
        checked.clear();
        if (keyList != null) {
            checked.addAll(keyList);
        }

        return this;
    }

    @Override
    public CheckboxGroupView setValue(String value) {
        if (value != null && !value.isEmpty()) {
            Arrays.asList(value.split(",")).forEach(v -> setChecked(v));
        }

        return this;
    }


}
