/*
Copyright © Cloudreach Europe Limited 2017. All rights reserved.
Cloudreach Europe Limited or a member of its group (“Cloudreach”) is the proprietor or licensee of all intellectual property rights contained in this document. 
Your use of this document or any information contained within is subject to your agreement with Cloudreach. 
Any unauthorised used is strictly prohibited. 
Unless otherwise stated in your agreement with Cloudreach, this document is provided without warranties to the fullest extent of the law. 

This document and contents are confidential. If you have received in error, do not distribute or make a copy and contact connect@cloudreach.com immediately.
 */
package com.cloudreach.x2.ui.util;

import org.junit.Assert;
import org.junit.Test;
import static com.cloudreach.x2.ui.util.Util.F;

/**
 *
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
			Assert.assertEquals(2,ds.query(dbConn, "select type from public.config")
				.map(r -> F(r,rs -> rs.getString("type")))
				.filter(t -> "GOOGLE".equals(t) || "SFDC".equals(t))
				.distinct().count());
		});
	}
}
