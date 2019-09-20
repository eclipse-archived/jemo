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

/**
 * @author Christopher Stura
 */
public class EmbeddedWebView extends AreaView {
    private String url = null;

    public EmbeddedWebView() {
        super();
    }

    public EmbeddedWebView(String externalURL) {
        this(12, externalURL);
    }

    public EmbeddedWebView(int size, String externalURL) {
        super(size);

        this.url = externalURL;
    }

    public String getUrl() {
        return url;
    }

    public EmbeddedWebView setUrl(String url) {
        this.url = url;
        return this;
    }
}
