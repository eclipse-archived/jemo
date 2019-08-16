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

/**
 * @author christopher stura
 */
public class WindowView extends PanelView {

    private int width = 0; //width in relative terms from 1 - 12 in line with the boostrap grid system.
    private boolean collapsable = true;
    private boolean closeable = false;

    public WindowView(String title, int width) {
        super(title);
    }

    public int getWidth() {
        return width;
    }

    public boolean isCollapsable() {
        return collapsable;
    }

    public boolean isCloseable() {
        return closeable;
    }

    @JsonIgnore
    public WindowView toggleCollapsable(boolean collapsable) {
        this.collapsable = collapsable;
        return this;
    }

    @JsonIgnore
    public WindowView toggleCloseable(boolean closeable) {
        this.closeable = closeable;
        return this;
    }
}
