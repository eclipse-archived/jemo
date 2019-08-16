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

import org.eclipse.jemo.ui.util.Util;
import org.eclipse.jemo.ui.view.FormComponentView;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * @author christopher stura
 */
public class DateView extends FormComponentView<DateView> {
    public static final SimpleDateFormat DATE_FORMAT = Util.DATE_SQL;

    private java.util.Date value = null;

    public DateView() {
        super();
    }

    public DateView(String name) {
        super(name);
    }

    @Override
    public DateView setValue(String value) {
        if (value != null) {
            try {
                this.value = Util.DATE_SQL.parse(value);
            } catch (ParseException ex) {
                this.value = null;
            }
        }
        return super.setValue(value); //To change body of generated methods, choose Tools | Templates.
    }

    @JsonIgnore
    public java.util.Date getValue() {
        return this.value;
    }

    @JsonIgnore
    public DateView setDateValueInMilliseconds(long value) {
        this.value = new java.util.Date(value);
        return this;
    }

    @JsonIgnore
    public DateView setDateValue(java.util.Date value) {
        this.value = value;
        return this;
    }

    @JsonProperty(value = "value")
    public String getDateString() {
        if (getValue() != null) {
            return Util.DATE_SQL.format(getValue());
        }

        return null;
    }
}
