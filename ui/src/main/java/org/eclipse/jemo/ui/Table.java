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
import org.eclipse.jemo.ui.view.TableView;

import java.util.List;
import java.util.Map;

/**
 * @author christopher stura
 */
public abstract class Table extends Component {
    public abstract List<TableRow> onDataPage(int page, int pageSize, String columnOrderBy, OrderDirection orderDirection, TableView view) throws Throwable;

    public abstract long onDataSize(TableView view) throws Throwable;

    @Override
    protected Object handleEvent(String event, Map<String, Object> parameters, String payload) throws Throwable {
        if (event.equalsIgnoreCase("onDataPage")) {
            TableView view = Util.fromJSON(TableView.class, payload);
            return onDataPage(Integer.parseInt(parameters.get("page").toString()), view.getPageSize(), parameters.get("orderby").toString(), OrderDirection.valueOf(parameters.getOrDefault("order_direction", "ASC").toString()), view);
        } else if (event.equalsIgnoreCase("onDataSize")) {
            TableView view = Util.fromJSON(TableView.class, payload);
            return onDataSize(view);
        }
        return super.handleEvent(event, parameters, payload); //To change body of generated methods, choose Tools | Templates.
    }


}
