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
package org.eclipse.jemo.internal.model;

import org.eclipse.jemo.Jemo;

import static org.eclipse.jemo.Jemo.logDateFormat;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;

/**
 * This class will manage logging for the Jemo application server.
 * <p>
 * The formatter will do the following.
 * 1. identify the module that is creating the log by using it's module name.
 * 2. construct the message to log and return that immediately through the logging framework.
 * 3. queue the message to a single item background thread that will write the log item to a temporary file. (named using a timestamp every 5 seconds)
 * 4. whenever a file is rotated (a new file is created) the logs in that file should be uploaded to the cloud provider.
 * 5. if the upload of the logs is successful then we delete the file, if not we reschedule the file for upload 5 seconds later.
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class JemoLogFormatter extends Formatter {

    private final ScheduledExecutorService LOG_FLUSH_SERVICE = Executors.newScheduledThreadPool(1);
    private final ExecutorService LOG_WRITE_THREAD = Executors.newSingleThreadExecutor();

    private final String LOCATION;
    private final String HOSTNAME;
    private final String INSTANCE_ID;

    public JemoLogFormatter(final String LOCATION, final String HOSTNAME, final String INSTANCE_ID) {
        this.LOCATION = LOCATION;
        this.HOSTNAME = HOSTNAME;
        this.INSTANCE_ID = INSTANCE_ID;
        LOG_FLUSH_SERVICE.scheduleWithFixedDelay(() -> {
            flushLogsToCloudRuntime();
        }, 30, 10, TimeUnit.SECONDS);
    }

    protected void flushLogsToCloudRuntime() {
        try {
            //lets get the first file in the directory which does not have the current file name.
            File tmpDir = new File(System.getProperty("java.io.tmpdir"));
            long start = System.currentTimeMillis();
            String currentLogName = getCurrentLogFileName();
            File[] logFiles = tmpDir.listFiles((pathname) -> pathname.getName().startsWith("jemo-cloud-log-") && !pathname.getName().equals(currentLogName) && pathname.lastModified() < start);
            if (logFiles != null && logFiles.length > 0) {
                //we should send all of the entries from this log file to our data stream, this will require loading all of the entires from the file into memory.
                File firstLogFile = Arrays.asList(logFiles).stream().filter(f -> f.length() > 0).findFirst().orElse(null);
                if (firstLogFile != null) {
                    List<CloudLogEvent> logEvents = flushLogFile(firstLogFile);

                    if (!logEvents.isEmpty()) {
                        if (CloudProvider.getInstance() == null) {
                            logEvents.forEach(logEvent -> System.out.print(logEvent.getMessage()));
                        } else {
                            CloudProvider.getInstance().getRuntime().log(logEvents);
                        }
                    }
                }
                //let's delete 0 length files
                Arrays.asList(logFiles).stream().filter(f -> f.length() <= 0).forEach(File::delete);
            }
        } catch (Throwable ex) {
            Jemo.log(Level.SEVERE, "[%s] I was unable to flush the logs written, to the global cloud log because of the error: %s", getClass().getSimpleName(), JemoError.toString(ex));
        }
    }

    protected List<CloudLogEvent> flushLogFile(File logFile) {
        List<CloudLogEvent> logEvents = new ArrayList<>();
        try (BufferedReader fin = new BufferedReader(new InputStreamReader(new FileInputStream(logFile)))) {
            String logLine;
            while ((logLine = fin.readLine()) != null) {
                logEvents.add(Jemo.fromJSONString(CloudLogEvent.class, logLine));
            }
        } catch (IOException ioEx) {
            Jemo.log(Level.SEVERE, "[%s][%s] I was unable to flush the logs written, to the global cloud log because of the error: %s", getClass().getSimpleName(), logFile.getName(), JemoError.toString(ioEx));
        } finally {
            logFile.delete();
        }//ignore read error
        return logEvents;
    }

    private static String getCurrentLogFileName() {
        long blockInterval = 30000; //30 second log block interval to reduce calls to cloudwatch
        long logFileSlot = System.currentTimeMillis() / blockInterval;
        logFileSlot = logFileSlot * blockInterval;

        return "jemo-cloud-log-" + String.valueOf(logFileSlot);
    }

    @Override
    public String format(LogRecord record) {
        //so a key issue with the logger is that the effort of formatting the log output is synchronous to the logging of the event we should actually delegate this effort to a background worker.
        StringBuilder logLine = new StringBuilder();
        logLine.append(logDateFormat.format(record.getMillis())).append(" [").append(LOCATION).append("]");
        logLine.append("[").append(HOSTNAME).append("][").append(INSTANCE_ID).append("]");
        logLine.append("[").append(record.getLevel().getLocalizedName()).append("]");
        String msg = record.getMessage().replaceAll("\\\n", " ").trim();
        logLine.append(" - {").append(record.getLoggerName()).append("} ").append(String.format(msg, record.getParameters()));

        if (record.getThrown() != null) {
            logLine.append(JemoError.newInstance(record.getThrown()));
        }
        logLine.append("\n");

        String[] loggerName = record.getLoggerName().split("\\:");
        CloudLogEvent logEvent = new CloudLogEvent(logLine.toString());
        logEvent.setLevel(record.getLevel().getLocalizedName());
        if (loggerName.length == 3) { //if we cannot identify a module then this will go to the default log.
            logEvent.setModuleId(Integer.parseInt(loggerName[0]));
            logEvent.setModuleVersion(Double.parseDouble(loggerName[1]));
            logEvent.setModuleName(loggerName[2]);
        }
        logEvent(logEvent);

        return logLine.toString(); //this should avoid writing the log entry
    }

    public void logEvent(CloudLogEvent logEvent) {
        LOG_WRITE_THREAD.submit(() -> {
            File logFile = new File(System.getProperty("java.io.tmpdir"), getCurrentLogFileName());
            try (PrintWriter fout = new PrintWriter(new FileOutputStream(logFile, true), false)) {
                fout.println(Jemo.toJSONString(logEvent));
                fout.flush();
            } catch (IOException ioEx) {
            } //error logging to file just ignore it (nothing we can do)
        });
    }
}
