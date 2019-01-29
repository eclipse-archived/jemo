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
package com.cloudreach.connect.x2.sys;

import com.cloudreach.connect.x2.internal.model.CloudProvider;
import com.cloudreach.connect.x2.internal.model.CloudQueueProcessor;
import com.cloudreach.connect.x2.internal.model.QueueDoesNotExistException;
import com.cloudreach.connect.x2.sys.internal.Util;
import com.cloudreach.x2.X2BaseTest;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import javax.xml.ws.Holder;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class TestCCQueueListener {
	@Test
	public void testIsDeadAndRun() throws Throwable {
		final List<String> LOG = new CopyOnWriteArrayList<>();
		CountDownLatch latch = new CountDownLatch(1);
		CountDownLatch latch2 = new CountDownLatch(1);
		try {
			CloudProvider.defineCustomeRuntime(new X2BaseTest.MockRuntime() {
				@Override
				public void store(String key, Object data) {}

				@Override
				public int pollQueue(String queueId, CloudQueueProcessor processor) throws QueueDoesNotExistException {
					throw new QueueDoesNotExistException("Queue does not exist");
				}

				@Override
				public String defineQueue(String queueName) {
					return "/"+UUID.randomUUID().toString()+"/"+queueName;
				}
			});
			final String instanceQueueUrl = "/"+UUID.randomUUID().toString()+"/CC-UNITTEST-"+UUID.randomUUID().toString();
			final Holder<String> newInstanceQueueUrl = new Holder<>(instanceQueueUrl);
			final X2BaseTest.TestX2Server x2server = new X2BaseTest.TestX2Server("UUID_"+UUID.randomUUID().toString(), "UNITTEST", 8080, "") {
				@Override
				public String getINSTANCE_QUEUE_URL() {
					return instanceQueueUrl;
				}

				@Override
				public void setINSTANCE_QUEUE_URL(String INSTANCE_QUEUE_URL) {
					super.setINSTANCE_QUEUE_URL(INSTANCE_QUEUE_URL);
					newInstanceQueueUrl.value = INSTANCE_QUEUE_URL;
					latch2.countDown();
				}

				@Override
				public void LOG(Level logLevel, String message, Object... args) {
					LOG.add(message);
					latch.countDown();
				}
			};
			CCQueueListener queueListener = new CCQueueListener(new ThreadGroup("test"), instanceQueueUrl, x2server);
			try {
				queueListener.start();
				assertFalse(queueListener.isDead());
				Util.setFieldValue(queueListener, "lastPoll", System.currentTimeMillis() - TimeUnit.HOURS.toMillis(3));
				assertTrue(queueListener.isDead());
				latch.await();
				assertEquals("Queue Does not Exist {%s} - %s retry in 20 seconds", LOG.iterator().next());
				latch2.await();
				assertFalse(instanceQueueUrl.equals(newInstanceQueueUrl.value));
			}finally {
				queueListener.interrupt();
			}
		}finally {
			CloudProvider.defineCustomeRuntime(null);
		}
	}
}
