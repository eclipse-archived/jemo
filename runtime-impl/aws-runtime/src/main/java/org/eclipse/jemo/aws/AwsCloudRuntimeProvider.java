package org.eclipse.jemo.aws;

import org.eclipse.jemo.internal.model.CloudRuntime;
import org.eclipse.jemo.internal.model.CloudRuntimeProvider;

import java.io.IOException;

/**
 * @author Yannis Theocharis
 */
public class AwsCloudRuntimeProvider implements CloudRuntimeProvider {

    @Override
    public CloudRuntime create() {
        return new AmazonAWSRuntime();
    }
}
