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
package org.eclipse.jemo.api;

import org.eclipse.jemo.internal.model.JemoMessage;
import org.eclipse.jemo.internal.model.CloudRuntime;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * a module represents a kernel artifact. This defines an entry point
 * for a loadable piece of code which can then be used to build an application which runs inside of Eclipse Jemo
 *
 * @author christopher stura
 */
public interface Module {
    class ModuleInfo {
        final Logger logger;
        final String name;
        final int id;
        final double version;

        public ModuleInfo(Logger logger, String name, int id, double version) {
            this.logger = logger;
            this.name = name;
            this.id = id;
            this.version = version;
        }
    }

    Map<Module, ModuleInfo> MODULE_INFO_MAP = new HashMap<>();

    default void construct(Logger logger, String name, int id, double version) {
        MODULE_INFO_MAP.put(this, new ModuleInfo(logger, name, id, version));
    } //lifecycle (called when the module is created for the first time by the kernel)

    default void installed() {
    } //called the first time the module is registered on the kernel.

    default void upgraded() {
    } //called whenever the module is replaced by a new version.

    default void start() {
    } //called whenever the module is started.

    default void stop() {
        MODULE_INFO_MAP.remove(this);
    } //called whenever the module is stopped.

    default void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
    } //this is called if the module needs to respond to an http/https request

    default JemoMessage process(JemoMessage message) throws Throwable {
        return null;
    }

    /**
     * this method will be called periodically once every minute by the build in scheduler engine.
     * if any batch or periodic tasks need to be executed you should implement this method.
     *
     * @param location        identifies the location where the module batch is being asked to run
     * @param isCloudLocation identifies whether the batch is currently being run in a public cloud location
     * @throws Throwable if an error processing the batch task was generated. Note that on error the current execution will not be re-tried but will run again at the next interval.
     */
    default void processBatch(String location, boolean isCloudLocation) throws Throwable {
    }

    default String getInstanceId() throws Throwable {
        return JemoMessage.getInstanceID();
    }

    default String getBasePath() {
        return null;
    } //if the module will respond to http requests the base path for forwarded requests must be returned by this method.

    default double getVersion() {
        return MODULE_INFO_MAP.get(this) == null ? 1.0 : MODULE_INFO_MAP.get(this).version;
    }

    default int getId() {
        return MODULE_INFO_MAP.get(this) == null ? 0 : MODULE_INFO_MAP.get(this).id;
    }

    //is called when the configuration parameters stored for this module are passed on into the module. Please not configuration is stored on a plugin by plugin basis so if a plugin has more than one module the
    //configuration passed to each of them will effectively be the same.
    default void configure(Map<String, String> configuration) {
    }

    /**
     * this method will give a module access to the cloud provider abstraction layer.
     * This will allow you to develop modules on Jemo that take advantage of multi-cloud abstraction and therefore become multi-cloud as well.
     * <p>
     * This default method is already a concrete implementation and therefore you should not override it in your module code.
     *
     * @return a reference to the current cloud runtime for the platform you are running on.
     * @throws ClassNotFoundException    if there was a problem getting access to the runtime component.
     * @throws NoSuchMethodException     if there was a problem with the underlying runtime components.
     * @throws IllegalAccessException    if there was an access exception imposed by the configured security manager.
     * @throws InvocationTargetException if there was a problem accessing the runtime dynamically.
     */
    default CloudRuntime getRuntime() {
        try {
            Class cloudProviderCls = Class.forName("org.eclipse.jemo.internal.model.CloudProvider");
            Object cloudProvider = cloudProviderCls.getMethod("getInstance").invoke(cloudProviderCls);
            return CloudRuntime.class.cast(cloudProviderCls.getMethod("getRuntime").invoke(cloudProvider));
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    /**
     * this method will allow you to set the execution limits of your module within the scope of the GSM, it's locations and it's instance composition
     *
     * @return the limit configuration for this module.
     */
    default ModuleLimit getLimits() {
        return ModuleLimit.defaultInstance();
    }

    /**
     * this method will allow you to define processes which by definition are always running. The quantity in which
     * these processes are run and how many of there are run at the same time and where can be controlled setting the correct limits
     * by overriding the getLimits method in your module.
     * <p>
     * this is a new pattern introduced in Jemo 2.3 which should make building resource intensive applications easier
     * <p>
     * Please note that active processes running as a result of the implementation of this pattern should be shut down by the implementation
     * when the stop lifecycle method is called.
     *
     * @param location   the location within the GSM where this process has been launched.
     * @param instanceId the id of the instance this process has been launched on.
     * @throws Throwable if the process terminated with an error in the body of the function. Please note a termination will cause the process to be re-spawned elsewhere
     */
    default void processFixed(final String location, final String instanceId) throws Throwable {
    }

    /**
     * Logs the specified message with the specified level
     *
     * @param level the LOG level
     * @param msg   the message to log
     */
    default void log(Level level, String msg) {
        MODULE_INFO_MAP.get(this).logger.log(level, msg);
    }

}
