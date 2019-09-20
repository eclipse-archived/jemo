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

/**
 * this class describes a view which contains only data and will not be visible to anyone.
 * the primary use of this view will be to manipulate the data inside it via client side
 * interactions.
 *
 * @author christopher stura
 */
public class DataView extends FormComponentView<DataView> {
    private String value = null;

    public DataView() {
    }

    public DataView(String name, String value) {
        super(name);

        this.value = value;
    }

    @JsonProperty(value = "value")
    public String getValue() {
        return value;
    }

    @Override
    @JsonProperty(value = "value")
    public DataView setValue(String value) {
        this.value = value;

        return this;
    }
}
