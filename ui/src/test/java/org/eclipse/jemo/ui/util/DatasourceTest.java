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
package org.eclipse.jemo.ui.util;

import org.junit.Assert;
import org.junit.Test;

import static org.eclipse.jemo.ui.util.Util.F;

/**
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class DatasourceTest {
    private static final Datasource ds;

    static {
        final String jdbcUrl = "";
        final String username = "";
        final String password = "";
        ds = new Datasource("org.postgresql.Driver", jdbcUrl, username, password);
    }

    @Test
    public void testDatasourceQueryStream() throws Throwable {
        ds.run(dbConn -> {
			/*System.out.println(ds.query(dbConn, "select type from public.config")
				.map(r -> F(r,rs -> rs.getString("type")))
				.filter(t -> "GOOGLE".equals(t) || "SFDC".equals(t))
				.distinct().collect(Collectors.toList()));*/
            Assert.assertEquals(2, ds.query(dbConn, "select type from public.config")
                    .map(r -> F(r, rs -> rs.getString("type")))
                    .filter(t -> "GOOGLE".equals(t) || "SFDC".equals(t))
                    .distinct().count());
        });
    }
}
