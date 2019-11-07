package org.eclipse.jemo.gcp;

import org.eclipse.jemo.internal.model.CloudRuntime;
import org.eclipse.jemo.internal.model.CloudRuntimeProvider;

/**
 * @author Yannis Theocharis
 */
public class GcpCloudRuntimeProvider implements CloudRuntimeProvider {

    @Override
    public CloudRuntime create() {
        return new GcpRuntime();
    }
}
