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

import org.eclipse.jemo.ui.util.Util;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class DataListViewTest {

    @Test
    public void testSerializeDeserialise() throws Throwable {
        KeyPairDataListView pkView = new KeyPairDataListView("test", new KeyPairDataListView.KeyPairDataListItem("k1", "v1"));
        assertEquals(KeyPairDataListView.class.getName(), pkView.getImplementation());
        DataListView view = (DataListView) Util.fromJSON(Class.forName(pkView.getImplementation()), Util.toJSON(pkView));
        assertEquals(1, view.getItems().size());
    }
}
