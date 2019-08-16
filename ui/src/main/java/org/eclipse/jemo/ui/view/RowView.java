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

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * @author christopher stura
 */
public class RowView extends View {
    private List<View> components = new ArrayList<>();

    public List<View> getComponents() {
        return components;
    }

    @JsonIgnore
    public WindowView addWindow(String title, int width) {
        WindowView w = new WindowView(title, width);
        getComponents().add(w);
        return w;
    }

    public RowView addComponent(View component) {
        component.setContainerId(getContainerId());
        getComponents().add(component);
        return this;
    }
}
