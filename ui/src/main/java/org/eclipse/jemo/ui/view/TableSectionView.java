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

import java.util.Objects;

/**
 * @author Chris
 */
public class TableSectionView implements Comparable<TableSectionView>, Cloneable {
    private String key = null;
    private String title = null;
    private int order = 0;

    public String getKey() {
        return key;
    }

    public TableSectionView setKey(String key) {
        this.key = key;
        return this;
    }

    public String getTitle() {
        return title;
    }

    public TableSectionView setTitle(String title) {
        this.title = title;
        return this;
    }

    public int getOrder() {
        return order;
    }

    public TableSectionView setOrder(int order) {
        this.order = order;
        return this;
    }

    @Override
    @JsonIgnore
    public int hashCode() {
        return getKey().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final TableSectionView other = (TableSectionView) obj;
        if (!Objects.equals(this.key, other.key)) {
            return false;
        }
        return true;
    }

    @Override
    public int compareTo(TableSectionView o) {
        return getKey().compareTo(o.getKey());
    }

    @JsonIgnore
    public TableSectionView copy() {
        TableSectionView copyView = new TableSectionView();
        copyView.setKey(key);
        copyView.setOrder(order);
        copyView.setTitle(title);

        return copyView;
    }
}
