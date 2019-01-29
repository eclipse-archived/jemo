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
package com.cloudreach.connect.x2.internal.model;

import com.cloudreach.connect.x2.AbstractX2;
import com.cloudreach.connect.x2.CC;
import com.cloudreach.x2.X2BaseTest;
import java.io.File;
import java.util.ArrayList;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestX2LogFormatter {
	
	@Test
	public void testLogFormatter() {
		String tempDir = System.getProperty("java.io.tmpdir");
		AbstractX2 origCC = CC.SERVER_INSTANCE;
		try {
			System.clearProperty("java.io.tmpdir");
			ArrayList<String> logMessage = new ArrayList<>();
			CC.SERVER_INSTANCE = new X2BaseTest.TestX2Server("UUID_"+UUID.randomUUID().toString(),"TEST",8080,"") {
				@Override
				public void LOG(Level logLevel, String message, Object... args) {
					logMessage.add(message);
				}
			};
			X2LogFormatter logFormatter = new X2LogFormatter("TEST1", "localhost", UUID.randomUUID().toString());
			logFormatter.flushLogsToCloudRuntime();
			assertEquals("[%s] I was unable to flush the logs written, to the global cloud log because of the error: %s", logMessage.iterator().next());
			logMessage.clear();
			logFormatter.flushLogFile(new File("/undefined", "rubbish"));
			assertEquals("[%s][%s] I was unable to flush the logs written, to the global cloud log because of the error: %s", logMessage.iterator().next());
			LogRecord testLogRecord = new LogRecord(Level.INFO, "message");
			testLogRecord.setThrown(new RuntimeException("test error"));
			testLogRecord.setLoggerName("TEST");
			assertTrue(logFormatter.format(testLogRecord).indexOf("[INFO] - {TEST} messageCCError{stackTrace=java.lang.RuntimeException: test error") != -1);
		}finally {
			System.setProperty("java.io.tmpdir", tempDir);
			CC.SERVER_INSTANCE = origCC;
		}
	}
}
