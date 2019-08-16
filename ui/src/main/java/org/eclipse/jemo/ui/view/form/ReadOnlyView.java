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
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.UUID;

/**
 * this represents a simple title and data pair which will be visible as read-only in a form.
 *
 * @author christopher stura
 */
public class ReadOnlyView extends FormComponentView<ReadOnlyView> {

    private String value = null;

    public ReadOnlyView(String title, String value) {
        super("readonly_" + UUID.randomUUID().toString());
        setTitle(title);
        setValue(value);
    }

    @Override
    @JsonProperty(value = "value")
    public ReadOnlyView setValue(String value) {
        this.value = value;
        return super.setValue(value); //To change body of generated methods, choose Tools | Templates.
    }

    @JsonProperty(value = "value")
    public String getValue() {
        return value;
    }
}
