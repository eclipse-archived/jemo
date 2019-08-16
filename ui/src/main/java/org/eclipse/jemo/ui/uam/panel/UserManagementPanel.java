/*
 ********************************************************************************
 * Copyright (c) 26th February 2019 Cloudreach Limited Europe
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
 ********************************************************************************
 */
package org.eclipse.jemo.ui.uam.panel;

import org.eclipse.jemo.ui.ApplicationFeature;
import org.eclipse.jemo.ui.Button;
import org.eclipse.jemo.ui.OrderDirection;
import org.eclipse.jemo.ui.Panel;
import org.eclipse.jemo.ui.Table;
import org.eclipse.jemo.ui.TableRow;
import org.eclipse.jemo.ui.view.AreaFormView;
import org.eclipse.jemo.ui.view.AreaView;
import org.eclipse.jemo.ui.view.ButtonView;
import org.eclipse.jemo.ui.view.PanelView;
import org.eclipse.jemo.ui.view.View;
import org.eclipse.jemo.ui.view.form.TextView;

import static org.eclipse.jemo.ui.util.i18n.*;

import org.eclipse.jemo.ui.view.TableView;

import java.util.List;

/**
 * this class will implement the user management panel interface.
 * <p>
 * the management panel will not directly access any of the data sources but will instead
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class UserManagementPanel extends Panel {

    @Override
    public PanelView onCreate(ApplicationFeature feature) throws Throwable {
        //the initial user management panel should show a list of the users in the system. By default users have the following properties
        //1. they can be active or inactive
        //2. they can be part of one or more groups.
        //3. they can have metadata associated to them. (key/value) data
        //4. they will have an unique id, a first name, a last name, an e-mail
        //5. we will track system metadata including:
        //	- the last time the logged in.
        //	- a list of the services their security profile allows them to access
        //	- a list of the services they have accessed including the last time they were accessed.
        //end users of the service may also want to add user attached functionality to the UI so make sure they can do this.
        //when we build the SPI we will include a default implementation which will use the default jemo cloud storage through the runtime,
        //but people may choose to implement the SPI for their specific application which many use a different storage backend.

        //the initial panel is a table of users with a button on the right hand side to create a new user. Newly created users
        //will always be pending their first login and will be identified by their e-mail address.
        AreaView tableAreaContainer = new AreaView(12);
        tableAreaContainer.addComponent(buildTableArea(feature));

        PanelView panel = new PanelView(feature.getTitle());
        panel.addRow()
                .addComponent(buildTopArea(feature, tableAreaContainer))
                .addComponent(tableAreaContainer);
        return panel;
    }

    /**
     * this method will construct a panel view which will display a for containing a button to create / invite new users
     * to the application and a search box which will allow users to find specific users in the system.
     *
     * @param feature a reference to the application feature from which this panel was triggered
     * @param targetArea the target area
     * @return an AreaFormView constructed with the necessary visual components and the correct state.
     */
    protected AreaFormView buildTopArea(ApplicationFeature feature, AreaView targetArea) {
        AreaFormView view = new AreaFormView(12);
        view.addComponent(new TextView("search"))
                .addComponent(new ButtonView(USER_ACCESS_MANAGEMENT.getString("btn.search.title"), targetArea.getId(), Search.class))
                .addComponent(new ButtonView(USER_ACCESS_MANAGEMENT.getString("btn.newuser.title"), NewUserDialog.class));
        return view;
    }

    protected AreaView buildTableArea(ApplicationFeature feature) {
        AreaView tableArea = new AreaView(12);
        TableView userTable = new TableView(UserSPITableController.class);
        userTable.addColumn("email", USER_ACCESS_MANAGEMENT.getString("tbl.user.email"), true);
        userTable.addColumn("firstname", USER_ACCESS_MANAGEMENT.getString("tbl.user.firstname"), true);
        userTable.addColumn("lastname", USER_ACCESS_MANAGEMENT.getString("tbl.user.lastname"), true);
        userTable.addColumn("groups", USER_ACCESS_MANAGEMENT.getString("tbl.user.groups"), true);
        userTable.addColumn("active", USER_ACCESS_MANAGEMENT.getString("tbl.user.active"), true);
        userTable.addColumn("lastlogin", USER_ACCESS_MANAGEMENT.getString("tbl.user.lastlogin"), true);
        tableArea.addComponent(userTable);
        return tableArea;
    }

    public static class Search extends Button {

        @Override
        public View onClick(ButtonView view) throws Throwable {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

    public static class NewUserDialog extends Button {

        @Override
        public View onClick(ButtonView view) throws Throwable {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

    }

    public static class UserSPITableController extends Table {

        @Override
        public List<TableRow> onDataPage(int page, int pageSize, String columnOrderBy, OrderDirection orderDirection, TableView view) throws Throwable {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }

        @Override
        public long onDataSize(TableView view) throws Throwable {
            throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
        }
    }
}
