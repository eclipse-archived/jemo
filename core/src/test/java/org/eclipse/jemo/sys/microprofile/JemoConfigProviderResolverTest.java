/*
********************************************************************************
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

package org.eclipse.jemo.sys.microprofile;

import static org.junit.Assert.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

import org.eclipse.jemo.JemoGSMTest;
import org.eclipse.jemo.api.EventModule;
import org.eclipse.jemo.api.KeyValue;
import org.eclipse.jemo.internal.model.JemoMessage;
import org.eclipse.microprofile.config.Config;
import org.junit.Test;

/**
 * The purpose of this test file is to ensure that the class org.eclipse.jemo.sys.microprofile.JemoConfigProviderResolver
 * obtains 100% code coverage from a unit test standpoint.
 * 
 * Because this class can only be used within an Active Jemo module we will need to instantiate the server to make sure these
 * tests run correctly.
 * 
 * @author Christopher Stura "cstura@gmail.com"
 */
public class JemoConfigProviderResolverTest extends JemoGSMTest {
	
	private static final ReentrantLock RUN_LOCK = new ReentrantLock();
	private static CountDownLatch PROCESS_LOCK = null;
	private static final String MSG_TYPE = "MESSAGE_TYPE";
	private static final String GET_CONFIG = "GET_CONFIG";
	private static final AtomicBoolean IS_DEPLOYED = new AtomicBoolean(false);
	private static final int APP_ID = 92000;
	
	private static AtomicReference<Object> RESULT = new AtomicReference<>(null);
	
	
	public static class TestModule implements EventModule {

		@Override
		public JemoMessage process(JemoMessage message) throws Throwable {
			JemoConfigProviderResolver configResolver = new JemoConfigProviderResolver();
			switch((String)message.getAttributes().get(MSG_TYPE)) {
			case GET_CONFIG:
				RESULT.set(configResolver.getConfig());
				break;
			}
			
			PROCESS_LOCK.countDown();
			return null;
		}
		
	}
	
	protected <T extends Object> T runThroughEvent(final String messageType,Class<T> returnType) throws Throwable {
		RUN_LOCK.lock();
		try {
			RESULT.set(null);
			if(IS_DEPLOYED.compareAndSet(false, true)) {
				uploadPlugin(APP_ID, 1.0, "TestModule", TestModule.class);
			}
			PROCESS_LOCK = new CountDownLatch(1);
			sendMessage(APP_ID, 1.0, TestModule.class, JemoMessage.LOCATION_ANYWHERE, 
					new KeyValue<>(MSG_TYPE, messageType));
			if(PROCESS_LOCK.await(30, TimeUnit.SECONDS)) {
				T ref = returnType.cast(RESULT.get());
				return ref;
			}
			
			return null;
		}finally {
			RUN_LOCK.unlock();
		}
	}
	
	@Test
	public void testGetConfig() throws Throwable {
		Config appConfig = runThroughEvent(GET_CONFIG, Config.class);
		assertNotNull(appConfig);
	}
}
