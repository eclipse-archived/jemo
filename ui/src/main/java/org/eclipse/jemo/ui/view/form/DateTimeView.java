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

import org.eclipse.jemo.ui.view.FormComponentView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.text.SimpleDateFormat;

/**
 * @author christopherstura
 */
public class DateTimeView extends FormComponentView<DateTimeView> {

    public static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm");

    private String value = null;

    public DateTimeView() {
        super();
    }

    public DateTimeView(String name) {
        super(name);
    }

    @JsonProperty(value = "value")
    public String getValue() {
        return value;
    }

    @Override
    @JsonProperty(value = "value")
    public DateTimeView setValue(String value) {
        this.value = value;
        return super.setValue(value);
    }

    @JsonIgnore
    public DateTimeView setValue(long value) {
        return setValue(DATETIME_FORMAT.format(new java.util.Date(value)));
    }

    @JsonIgnore
    public DateTimeView setValue(java.util.Date value) {
        return setValue(DATETIME_FORMAT.format(value));
    }

    @JsonIgnore
    public DateTimeView setValue(java.util.Calendar value) {
        return setValue(DATETIME_FORMAT.format(value.getTime()));
    }
}
