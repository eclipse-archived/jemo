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
package org.eclipse.jemo.ui;

import org.eclipse.jemo.ui.util.Util;
import org.eclipse.jemo.ui.view.View;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.ArrayList;
import java.util.List;

/**
 * this class represents the model with which the user menu component will be
 * returned to the application framework.
 *
 * @author Christopher Stura
 */
public class UserMenu extends View {
    private final List<UserMenuItem> items = new ArrayList<>();

    public List<UserMenuItem> getItems() {
        addDivider();
        add("Logout", "document.location.href=document.location.pathname;");
        return items;
    }

    @JsonIgnore
    public UserMenu add(String title, Class<? extends Button> action) {
        if (!items.parallelStream().anyMatch(i -> i.getTitle().equalsIgnoreCase(title))) {
            items.add(new UserMenuItem().setAction(action).setTitle(title));
        }
        return this;
    }

    @JsonIgnore
    public UserMenu add(String title, String javascriptAction) {
        if (!items.parallelStream().anyMatch(i -> Util.S(i.getTitle()).equalsIgnoreCase(title))) {
            items.add(new UserMenuItem().setTitle(title).setTarget("javascript:" + javascriptAction));
        }
        return this;
    }

    @JsonIgnore
    public UserMenu addDivider() {
        items.add(new UserMenuItem().setDivider(true));
        return this;
    }
}
