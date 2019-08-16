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
package org.eclipse.jemo.ui.panel;

import org.eclipse.jemo.ui.Application;
import org.eclipse.jemo.ui.ApplicationFeature;
import org.eclipse.jemo.ui.Button;
import org.eclipse.jemo.ui.ICON;
import org.eclipse.jemo.ui.Panel;
import org.eclipse.jemo.ui.view.ButtonView;
import org.eclipse.jemo.ui.view.EmbeddedWebView;
import org.eclipse.jemo.ui.view.PanelView;
import org.eclipse.jemo.ui.view.View;

/**
 * this panel will embed an external web site within a Jemo application
 *
 * @author Christopher Stura
 */
public class URLPanel extends Panel {

    protected static final String ATTR_EXTERNAL_URL = "_x2_sys_urlpanel_url";

    public URLPanel() {
    }

    protected static String getExternalURL(View feature) {
        return feature.getAttributeAsString(ATTR_EXTERNAL_URL);
    }

    @Override
    public PanelView onCreate(ApplicationFeature feature) throws Throwable {
        PanelView panel = new PanelView(feature.getTitle());

        panel.addRow().addComponent(new EmbeddedWebView(getExternalURL(feature)));
        return panel;
    }

    public static ButtonView buildAction(String title, String externalUrl) {
        return buildAction(title, Application.MAIN_APPLICATION_AREA, externalUrl);
    }

    public static ButtonView buildAction(String title, String target, String externalUrl) {
        return new ButtonView(title, target, URLPanelAction.class).setAttribute(ATTR_EXTERNAL_URL, externalUrl);
    }

    public static ApplicationFeature buildFeature(ICON icon, String title, String externalUrl) {
        return new ApplicationFeature(URLPanelAction.class, icon, title).setAttribute(ApplicationFeature.class, ATTR_EXTERNAL_URL, externalUrl);
    }

    public static class URLPanelAction extends Button {

        @Override
        public View onClick(ButtonView view) throws Throwable {
            URLPanel panel = new URLPanel();
            panel.setApplication(getApplication());
            return panel.onCreate(buildFeature(ICON.cog, view.getTitle(), getExternalURL(view)).copyAttributesFrom(view));
        }

    }
}
