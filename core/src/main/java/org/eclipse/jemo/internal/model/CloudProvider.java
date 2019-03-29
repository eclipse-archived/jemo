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

import org.eclipse.jemo.runtime.MemoryRuntime;
import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.internal.Util;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.ByteArrayOutputStream;
import java.util.List;

import static org.eclipse.jemo.api.JemoParameter.CLOUD;
import static org.eclipse.jemo.sys.internal.Util.readParameterFromJvmOrEnv;
import static java.util.Arrays.asList;

/**
 * this enumeration will help to identify what cloud provider we are running on
 * based on this key we will use different infrastructure components in the runtime.
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
@JsonFormat(shape = JsonFormat.Shape.OBJECT)
public enum CloudProvider {
    AWS("org.eclipse.jemo.internal.model.AmazonAWSRuntime",
            null,
            "AWS",
            "https://aws.amazon.com/",
            "Amazon Web Services",
            asList(AmazonAWSRuntime.AWS_ACCESS_KEY_ID, AmazonAWSRuntime.AWS_SECRET_ACCESS_KEY)
    ),

    AZURE("org.eclipse.jemo.runtime.azure.MicrosoftAzureRuntime",
            "runtime-jars/azure-runtime.jar",
            "AZURE",
            "https://azure.microsoft.com/",
            "Microsoft Azure",
            asList("tenant_id", "client_id", "client_secret")),

    MEMORY("org.eclipse.jemo.runtime.MemoryRuntime",
            null,
            "MEMORY",
            null,
            "A main memory CSP mock",
            asList(MemoryRuntime.USER, MemoryRuntime.PASSWORD));

    private static CloudRuntime defaultRuntime = null;
    private static CloudProvider CLOUD_PROVIDER;

    @JsonIgnore
    CloudRuntime runtime = null;

    @JsonIgnore
    String runtimeClass;

    @JsonIgnore
    String implementation;

    @JsonProperty
    private final String name;

    @JsonProperty
    private final String url;

    @JsonProperty
    private final String description;

    @JsonProperty
    private final List<String> requiredCredentials;

    CloudProvider(String runtimeClass, String implementation, String name, String url, String description, List<String> requiredCredentials) {
        this.runtimeClass = runtimeClass;
        this.implementation = implementation;
        this.name = name;
        this.url = url;
        this.description = description;
        this.requiredCredentials = requiredCredentials;
    }

    /**
     * modify this so that it is possible for a cloud provider not to be detected.
     *
     * @return a reference to an activated cloud provider (meaning we can access that CSP and run all operations necessary for the runtime) or null if no valid provider can be identified.
     */
    public static CloudProvider getInstance() {
        if (CLOUD_PROVIDER != null) {
            return CLOUD_PROVIDER;
        }

        final String jemoCloudProperty = readParameterFromJvmOrEnv(CLOUD.label());
        if (jemoCloudProperty != null) {
            final CloudProvider cloudProvider = fromName(jemoCloudProperty);
            if (cloudProvider.getRuntime().validatePermissions().isSuccess()) {
                return CLOUD_PROVIDER = cloudProvider;
            } else {
                // The user asked for a specific provider, we should not try the others.
                return null;
            }
        }

        for (CloudProvider cloudProvider : CloudProvider.values()) {
            if (cloudProvider.getRuntime().validatePermissions().isSuccess()) {
                return CLOUD_PROVIDER = cloudProvider;
            }
        }

        return null;
    }

    public static CloudProvider fromName(final String cloudProviderName) {
        for (CloudProvider provider : CloudProvider.values()) {
            if (provider.name().equalsIgnoreCase(cloudProviderName)) {
                return provider;
            }
        }
        throw new IllegalStateException("Invalid cloud provider name: " + cloudProviderName);
    }

    /**
     * Returns the runtime that matches the specified cloud provider name.
     *
     * @param cloudProviderName the cloud provider name
     * @return the runtime matching the cloud provider name
     */
    public static CloudRuntime getRuntimeByName(final String cloudProviderName) {
        return fromName(cloudProviderName).getRuntime();
    }

    private synchronized void initializeRuntime() {
        Util.B(null, x -> {
            if (implementation != null) {
                //we can avoid initializing this if we need to
                ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
                Util.stream(byteOut, getClass().getResourceAsStream("/" + implementation));
                JemoClassLoader cl = new JemoClassLoader(runtimeClass, byteOut.toByteArray());
                Class cloudRuntime = cl.loadClass(runtimeClass);
                runtime = CloudRuntime.class.cast(cloudRuntime.newInstance());
            } else {
                Class cloudRuntime = Class.forName(runtimeClass);
                runtime = CloudRuntime.class.cast(cloudRuntime.newInstance());
            }
        });
    }

    public CloudRuntime getRuntime() {
        if (defaultRuntime == null) {
            if (this.runtime == null) {
                //we need to initialize the runtime
                initializeRuntime();
            }
            return this.runtime;
        }
        return defaultRuntime;
    }

    public static final void defineCustomeRuntime(CloudRuntime runtime) {
        CloudProvider.defaultRuntime = runtime;
    }

    @JsonProperty
    public List<RegionInfo> getRegions() {
        return runtime == null ? null : runtime.getRegions();
    }

    @JsonProperty
    public AdminUserCreationInstructions getAdminUserCreationInstructions() {
        return runtime == null ? null : runtime.getAdminUserCreationInstructions();
    }

    @JsonProperty
    public List<InstallProperty> getInstallProperties() {
        return runtime == null ? null : runtime.getInstallProperties();
    }

}
