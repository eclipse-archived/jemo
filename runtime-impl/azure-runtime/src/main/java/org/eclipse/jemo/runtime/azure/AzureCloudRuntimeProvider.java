package org.eclipse.jemo.runtime.azure;

import org.eclipse.jemo.internal.model.CloudRuntime;
import org.eclipse.jemo.internal.model.CloudRuntimeProvider;

import java.io.IOException;

/**
 * @author Yannis Theocharis
 */
public class AzureCloudRuntimeProvider implements CloudRuntimeProvider {

    @Override
    public CloudRuntime create() {
        try {
            return new MicrosoftAzureRuntime();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
