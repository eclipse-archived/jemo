/*
Copyright © Cloudreach Europe Limited 2017. All rights reserved.
Cloudreach Europe Limited or a member of its group (“Cloudreach”) is the proprietor or licensee of all intellectual property rights contained in this document. 
Your use of this document or any information contained within is subject to your agreement with Cloudreach. 
Any unauthorised used is strictly prohibited. 
Unless otherwise stated in your agreement with Cloudreach, this document is provided without warranties to the fullest extent of the law. 

This document and contents are confidential. If you have received in error, do not distribute or make a copy and contact connect@cloudreach.com immediately.
 */
package com.cloudreach.x2.ui.util;

import junit.framework.Assert;
import org.junit.Test;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class UtilTest {
	@Test
	public void testTruncate() {
		Assert.assertEquals(3, Util.truncate("Chris", 3).length());
		Assert.assertEquals(5, Util.truncate("Chris",6).length());
		Assert.assertEquals(0, Util.truncate("Chris", 0).length());
		Assert.assertNull(Util.truncate(null,32));
	}
}
