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

import com.cloudreach.connect.x2.AbstractX2;
import com.cloudreach.connect.x2.CC;
import static com.cloudreach.connect.x2.CC.log;
import com.cloudreach.connect.x2.internal.model.CCError;
import com.cloudreach.connect.x2.internal.model.CloudProvider;
import com.cloudreach.connect.x2.sys.internal.Util;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

/**
 * this class will run in the background and every 5 minutes
 * write it's instance id and the time it sent the message to a table on dynamodb.
 * 
 * This thread will also monitor all of the entries in dynamodb to see if any of the queue's have been inactive
 * for more than 2 hours. If they have been inactive then they will be automatically deleted by the watchdog.
 * 
 * @author christopher stura
 */
public class CCWatchdog extends Thread {
	
	private final AbstractX2 x2server;
	
	public CCWatchdog(AbstractX2 x2server) {
		this.x2server = x2server;
	}
	
	@Override
	public void run() {
		try {
			//another thing we should look out for is if any of the event processing threads have stopped (as it appears this could happen).
			ConcurrentLinkedQueue<CCQueueListener> newQueueListeners = new ConcurrentLinkedQueue<>();
			x2server.getQUEUE_LISTENERS().parallelStream().forEach(ql -> {
				//now we should check if the last poll time for any event processing was more than 3 hours ago then we need to flag this queue listener as dead and restart it.
				if(ql.isDead()) {
					x2server.LOG(Level.WARNING, "[%s] queue listener was found dead and was restarted. It last polled the queue at %s and the poll lasted %d seconds", ql.getQueueUrl(), 
						CC.logDateFormat.format(new java.util.Date(ql.getLastPoll())), TimeUnit.SECONDS.convert(ql.getLastPollDuration(), TimeUnit.MILLISECONDS));
					newQueueListeners.add(ql.restart());
				} else {
					newQueueListeners.add(ql);
				}
			});
			x2server.getQUEUE_LISTENERS().clear();
			x2server.getQUEUE_LISTENERS().addAll(newQueueListeners);
			System.gc(); //force garbage collection to keep memory usage low. if we are running on a server VM this will be essential.
			log(Level.INFO,"[CC][Watchdog] System health check has completed successfully. Last Batch Run On: [%s] Batch Current Running: [%s] Last Scheduler Run [%s] Last Scheduler Poll [%s]",
					CC.logDateFormat.format(new java.util.Date(x2server.getLastBatchRunDate())), String.valueOf(x2server.getBatchRunning()), CC.logDateFormat.format(new java.util.Date(x2server.getLastSchedulerRun())),
					CC.logDateFormat.format(new java.util.Date(x2server.getLAST_SCHEDULER_POLL())));
			
			//we should check for any modules that were last used more than 10 minutes ago and if they were we will just simply unload them.
			x2server.getPluginManager().getApplicationList().stream()
				.filter(app -> CCPluginManager.PLUGIN_ID(app.getId()) != 0)
				.filter(app -> System.currentTimeMillis() - app.getLastUsedOn() > TimeUnit.MINUTES.toMillis(10))
				.forEach(app -> Util.B(null, x -> x2server.getPluginManager().unloadModule(app.getId())));
			
			CloudProvider.getInstance().getRuntime().watchdog(x2server.getLOCATION(),x2server.getINSTANCE_ID(),x2server.getINSTANCE_QUEUE_URL());
		}catch(Throwable ex) {
			x2server.LOG(Level.INFO,"[CC][Watchdog] Error running watchdog: %s", CCError.toString(ex));
		}
	}
}
