/*
Copyright © Cloudreach Europe Limited 2017. All rights reserved.
Cloudreach Europe Limited or a member of its group (“Cloudreach”) is the proprietor or licensee of all intellectual property rights contained in this document. 
Your use of this document or any information contained within is subject to your agreement with Cloudreach. 
Any unauthorised used is strictly prohibited. 
Unless otherwise stated in your agreement with Cloudreach, this document is provided without warranties to the fullest extent of the law. 

This document and contents are confidential. If you have received in error, do not distribute or make a copy and contact connect@cloudreach.com immediately.
 */
package com.cloudreach.x2.ui.view.form;

import com.cloudreach.x2.ui.util.Util;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class DataListViewTest {
	
	@Test
	public void testSerializeDeserialise() throws Throwable {
		KeyPairDataListView pkView = new KeyPairDataListView("test", new KeyPairDataListView.KeyPairDataListItem("k1", "v1"));
		assertEquals(KeyPairDataListView.class.getName(),pkView.getImplementation());
		DataListView view = (DataListView)Util.fromJSON(Class.forName(pkView.getImplementation()), Util.toJSON(pkView));
		assertEquals(1,view.getItems().size());
	}
}
