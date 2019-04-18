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
package org.eclipse.jemo.sys;

import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.Jemo;
import org.eclipse.jemo.api.ModuleLimit;
import org.eclipse.jemo.internal.model.*;
import org.eclipse.jemo.internal.model.JemoError;
import org.eclipse.jemo.sys.internal.Util;
import com.fasterxml.jackson.core.JsonProcessingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 *
 * @author christopher stura
 */
public class JemoQueueListener extends Thread {
	private static final Random RANDOM = new Random(System.currentTimeMillis());
	
	private String queueUrl = null;
	private long lastPoll = 0;
	private long lastPollDuration = 0;
	private volatile boolean interrupted = false;
	private ScheduledExecutorService resendScheduler = Executors.newScheduledThreadPool(1);
	private final AbstractJemo jemoServer;
	private final List<JemoMessage> DELAYED_MESSAGE_QUEUE = new CopyOnWriteArrayList<>();
	private ScheduledFuture DELAYED_MESSAGE_PROCESSOR = null;
	
	
	public JemoQueueListener(ThreadGroup group, String queueUrl, AbstractJemo jemoServer) {
		super(group, queueUrl);
		this.queueUrl = queueUrl;
		this.jemoServer = jemoServer;
	}

	@Override
	public synchronized void start() {
		super.start(); //To change body of generated methods, choose Tools | Templates.
		DELAYED_MESSAGE_PROCESSOR = resendScheduler.scheduleWithFixedDelay(() -> {
			if(!DELAYED_MESSAGE_QUEUE.isEmpty()) {
				List<JemoMessage> msgToSchedule = new ArrayList<>();
				msgToSchedule.addAll(DELAYED_MESSAGE_QUEUE);
				Set<String> msgIdList = msgToSchedule.stream().map(msg -> msg.getId()).collect(Collectors.toSet());
				DELAYED_MESSAGE_QUEUE.removeIf(msg -> msgIdList.contains(msg.getId()));
				//now reschedule each of the messages
				msgToSchedule.stream().forEach(msg -> scheduleMessage(msg));
			}
		}, 5, 5, TimeUnit.SECONDS);
	}

	public String getQueueUrl() {
		return queueUrl;
	}

	public long getLastPoll() {
		return lastPoll;
	}

	public long getLastPollDuration() {
		return lastPollDuration;
	}
	
	public JemoQueueListener restart() {
		interrupt();
		JemoQueueListener newListener = new JemoQueueListener(getThreadGroup(), queueUrl, jemoServer);
		newListener.start();
		return newListener;
	}

	public boolean isDead() {
		if(isAlive() && !interrupted) {
			if(System.currentTimeMillis()-lastPoll > TimeUnit.MILLISECONDS.convert(2, TimeUnit.HOURS)) {
				return true;
			}
			
			return false;
		}
	
		return true;
	}

	@Override
	public void interrupt() {
		jemoServer.LOG("("+getClass().getName()+":"+this.queueUrl+") interrupted. Shutdown sequence initiated",Level.INFO);
		interrupted = true;
		DELAYED_MESSAGE_QUEUE.clear();
		DELAYED_MESSAGE_PROCESSOR.cancel(true);
		resendScheduler.shutdownNow();
		try { resendScheduler.awaitTermination(20, TimeUnit.SECONDS); } catch(InterruptedException irrEx) {}
		super.interrupt();
	}
	
	private static final AtomicInteger executed = new AtomicInteger(0);
	private static final AtomicInteger submitted = new AtomicInteger(0);
	
	@Override
	public void run() {
		while(!interrupted) {
			long start = System.currentTimeMillis();
			lastPoll = start;
			try {
				if(queueUrl.equals(jemoServer.getINSTANCE_QUEUE_URL())) {
					CloudProvider.getInstance().getRuntime().store(jemoServer.getINSTANCE_ID()+".lastpoll", System.currentTimeMillis());
				}
				int messagesProcessed = CloudProvider.getInstance().getRuntime().pollQueue(queueUrl, (msg) -> {
					scheduleMessage(msg);
				});
				if(messagesProcessed == 0) {
					Thread.sleep(jemoServer.getQUEUE_POLL_WAIT_TIME()); //sleep 20 sec if there was nothing in the queue.
				} else {
					Thread.sleep(RANDOM.nextInt(15)+1);
				}
			}catch(InterruptedException irrEx) {
			}catch(QueueDoesNotExistException exEx) {
				if(!interrupted) {
					jemoServer.LOG(Level.SEVERE, "Queue Does not Exist {%s} - %s retry in 20 seconds",new Object[] { this.queueUrl, exEx.getMessage() });
					//we can attempt to re-create the queue as it may have been deleted.
					if(queueUrl.equals(jemoServer.getINSTANCE_QUEUE_URL())) {
						try {
							jemoServer.setINSTANCE_QUEUE_URL(CloudProvider.getInstance().getRuntime().createInstanceQueue(jemoServer.getLOCATION(),jemoServer.getINSTANCE_ID()));
						} catch(Exception ex) {}
					}
					try { Thread.sleep(20000); } catch(InterruptedException ex) {}
				}
			}catch(Throwable ex) {
				if(!interrupted) {
					jemoServer.LOG(Level.SEVERE, "[Jemo][QueueListener][%s] Unhandled error processing queue %s retry in 20 seconds",new Object[] { this.queueUrl, ex.getMessage() });
					try { Thread.sleep(20000); } catch(InterruptedException irrEx) {}
				}
			} finally {
				long end = System.currentTimeMillis();
				lastPoll = end;
				lastPollDuration = end-start;
			}
		}
	}
	
	/**
	 * this method will schedule a message for immediate or delayed execution taking the execution rules of the destination
	 * module into account when scheduling is done.
	 * 
	 * @param msg the message to process.
	 */
	public final void scheduleMessage(JemoMessage msg) {
		if(msg.getPluginId() == 0 || jemoServer.getPluginManager().PLUGIN_VALID(msg.getPluginId())) {
			if(msg.getPluginId() == 0) {
				submitMessage(msg);
			} else {
				//now if we are to submit this we need to check if the execute limits are respected on this instance.
				JemoApplicationMetaData app = jemoServer.getPluginManager().getApplication(msg.getPluginId(), msg.getPluginVersion());

				if (app == null) {
					// The app is deleted or deactivated, therefore there is nothing to submit
					return;
				}

				//get the current execution count for this instance
				ModuleLimit appLimits = app.getLimits().get(msg.getModuleClass());
				if(appLimits == null || (appLimits.getEventFrequency() == null && appLimits.getMaxActiveEventsPerGSM() <= 0 && appLimits.getMaxActiveEventsPerLocation() <= 0 && appLimits.getMaxActiveEventsPerInstance() <= 0)) {
					submitMessage(msg); //now limits apply and neither does a defined execution frequency.
				} else {
					boolean submit = true;
					if(appLimits.getEventFrequency() != null) {
						long lastLaunchedOn = jemoServer.getPluginManager().getLastLaunchedModuleEvent(msg.getPluginId(), msg.getPluginVersion(), msg.getModuleClass());
						if(lastLaunchedOn != 0 && System.currentTimeMillis() - lastLaunchedOn < appLimits.getEventFrequency().getUnit().toMillis(appLimits.getEventFrequency().getValue())) {
							queueMessage(msg);
							submit = false;
						} else {
							jemoServer.LOG(Level.INFO,"[%s][%s][Frequency] - The current module frequency means it's ok to launch. The last time this module was launched was %d (ms) ago and is allowed to be launched every %d (ms)", getClass().getSimpleName(), msg.getModuleClass(),
								lastLaunchedOn == 0 ? 0 : System.currentTimeMillis() - lastLaunchedOn, appLimits.getEventFrequency().getUnit().toMillis(appLimits.getEventFrequency().getValue()));
						}
					}
					if(submit) {
						int numRunning = jemoServer.getPluginManager().getNumModuleEventsRunning(msg.getPluginId(), msg.getPluginVersion(), msg.getModuleClass()); //the number of events running here.
						int numRunningLocation = jemoServer.getPluginManager().getNumModuleEventsRunningOnLocation(msg.getPluginId(), msg.getPluginVersion(), msg.getModuleClass()); //the number currently running at this location.
						int numRunningGSM = jemoServer.getPluginManager().getNumModuleEventsRunningOnGSM(msg.getPluginId(), msg.getPluginVersion(), msg.getModuleClass());  //the number currently running across the GSM.
						if(appLimits.getMaxActiveEventsPerGSM() != -1 && appLimits.getMaxActiveEventsPerGSM() > numRunningGSM) {
							submitMessage(msg);
						} else if(appLimits.getMaxActiveEventsPerLocation() != -1 && appLimits.getMaxActiveEventsPerLocation() > numRunningLocation) {
							submitMessage(msg);
						} else if(appLimits.getMaxActiveEventsPerInstance() != -1 && appLimits.getMaxActiveEventsPerInstance() > numRunning) {
							submitMessage(msg);
						} else if(appLimits.getMaxActiveEventsPerGSM() == -1 && appLimits.getMaxActiveEventsPerLocation() == -1 && appLimits.getMaxActiveEventsPerInstance() == -1) {
							submitMessage(msg);
						} else {
							queueMessage(msg);
						}
					}
				}
			}
		} else {
			//we should not wrap this message in a thread.
			processMessage(msg);
		}
	}
	
	private void queueMessage(JemoMessage msg) {
		DELAYED_MESSAGE_QUEUE.add(msg);
	}

	private void submitMessage(JemoMessage msg) {
		//If there are two versions 1.0 and 2.0 of the same plugin,
		// then if 'writeExecuteModuleEvent' was called outside of the submitted lamda the following bug would occur:
		// writeExecuteModuleEvent increases the counter for version 1.0,
		// then the processMessage method changes the version from 1.0 to 2.0
		// and then deleteExecuteModuleEvent decreases the counter for version 2.0.

        final String moduleClass = (msg.getModuleClass().equals(Jemo.class.getName()) && msg.getAttributes().containsKey("module_class")) ? (String)msg.getAttributes().get("module_class") : msg.getModuleClass();
		jemoServer.getEVENT_EXECUTOR().submit(()-> {
			try {
                jemoServer.getPluginManager().writeExecuteModuleEvent(msg.getPluginId(), msg.getPluginVersion(), moduleClass);
				jemoServer.LOG(Level.FINE,"QUEUE [%s] executed %d submitted %d", queueUrl, executed.addAndGet(1), submitted.get());
				processMessage(msg);
				jemoServer.LOG(Level.FINE,"QUEUE [%s] executed %d submitted %d finished %s", queueUrl, executed.decrementAndGet(), submitted.decrementAndGet(), msg.getAttributes().toString());
			}finally {
				jemoServer.getPluginManager().deleteExecuteModuleEvent(msg.getPluginId(), msg.getPluginVersion(), moduleClass);
			}
		});
		submitted.incrementAndGet();
	}
	
	public final void processMessage(JemoMessage msg) {
		try {
			if(msg.getPluginId() == 0 || jemoServer.getPluginManager().PLUGIN_VALID(msg.getPluginId())) {
				jemoServer.sys_processMessage(msg);
			} else if(!queueUrl.equals(jemoServer.getINSTANCE_QUEUE_URL())) {
				//we need to re-publish the message but only if the queue url is not related to this instance. (this will also allow processing of messages) which were not originally recieved.
				//the first step will be to make sure that we are not re-sending messages for modules which nobody is running (otherwise they will bounce back and forth forever)
				if(jemoServer.getPluginManager().getLiveModuleList(jemoServer.getLOCATION()).stream().anyMatch(m -> m.getId() == msg.getPluginId())) {
					CloudProvider.getInstance().getRuntime().sendMessage(queueUrl, Jemo.toJSONString(msg));
				} else {
					jemoServer.LOG(Level.WARNING, "[QueueListener][%s][%d] Message Discarded %s", queueUrl, msg.getPluginId(), Jemo.toJSONString(msg));
				}
				//so this is basically an infinate loop caused by the fact that we will delete messages off the queue immediately
				//even if we should not be processing them, in reality we should validate the messages to make sure they can in-fact be locally processed
				//before removing them from the queue. what we need to do is find out which modules are active in the cluster so we know whether to re-forward this message or simply just drop it.
				
			} else {
				jemoServer.LOG(Level.WARNING, "[QueueListener][%s][%d] Message Discarded %s", queueUrl, msg.getPluginId(), Jemo.toJSONString(msg));
			}
		}catch(Throwable ex) {
			if(!(ex instanceof InterruptedException)) {
				try {
					if(msg.getExecutionCount() < 25) {
						Util.B(null, y -> jemoServer.getPluginManager().runWithModuleContext(Void.class, x -> {
							if(!(ex instanceof TooMuchWorkException)) {
								msg.setLastError(JemoError.newInstance(ex));
							}
							//it is incorrect to republish messages to the location queue. They should be re-published to the same queue they came from.
							//if there was an error running this it does not make sense to resend it immediately we should wait 1000 for each time it has failed before re-sending it
							resendScheduler.schedule(() -> {
								Util.B(null, a -> jemoServer.getPluginManager().runWithModuleContext(Void.class, z -> {
									msg.send(queueUrl); //send the message back through the queue
									return null;
								}));
							}, (msg.getExecutionCount()+1)*10, TimeUnit.SECONDS);
							return null;
						}));
					} else {
						jemoServer.LOG(Level.WARNING, "Discarded message: %s to many errors", Jemo.toJSONString(msg));
					}
				}catch(JsonProcessingException republishEx) {} //this is not an important exception to handle because it would will never happen anyway.
			}
		}
	}
}
