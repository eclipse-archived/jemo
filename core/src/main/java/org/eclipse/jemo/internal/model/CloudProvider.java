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
import org.eclipse.jemo.sys.internal.Util;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    AWS("AWS",
            "https://aws.amazon.com/",
            "Amazon Web Services",
            "org.eclipse.jemo.aws.AwsCloudRuntimeProvider",
            asList("aws_access_key_id", "aws_secret_access_key")
    ),

    AZURE("AZURE",
            "https://azure.microsoft.com/",
            "Microsoft Azure",
            "org.eclipse.jemo.runtime.azure.AzureCloudRuntimeProvider",
            asList("tenant_id", "client_id", "client_secret")),

    GCP("GCP",
            "https://cloud.google.com/",
            "Google Cloud",
            "org.eclipse.jemo.gcp.GcpCloudRuntimeProvider",
            asList("project_id", "service_account_id")),

    MEMORY("MEMORY",
            null,
            "A main memory CSP mock",
            "",
            asList(MemoryRuntime.USER, MemoryRuntime.PASSWORD));

    private static CloudRuntime defaultRuntime = null;
    private static CloudProvider CLOUD_PROVIDER;

    @JsonIgnore
    CloudRuntime runtime = null;

    @JsonProperty
    private final String name;

    @JsonProperty
    private final String url;

    @JsonProperty
    private final String description;

    private final String providerFQClassName;

    @JsonProperty
    private final List<String> requiredCredentials;

    CloudProvider(String name, String url, String description, String providerFQClassName, List<String> requiredCredentials) {
        this.name = name;
        this.url = url;
        this.description = description;
        this.providerFQClassName = providerFQClassName;
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
        if (this == MEMORY) {
            runtime = new MemoryRuntime();
        } else {
            CloudRuntimeProvider provider = CloudRuntimeImplLoader.provider(this);
            runtime = provider.create();
        }
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

    public String getProviderFQClassName() {
        return providerFQClassName;
    }

    public String getName() {
        return name;
    }
}
