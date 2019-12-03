package org.eclipse.jemo.sys.microprofile;

import org.eclipse.microprofile.health.HealthCheckResponseBuilder;
import org.eclipse.microprofile.health.spi.HealthCheckResponseProvider;

public class JemoHealthCheckResponseProvider implements HealthCheckResponseProvider {

	@Override
	public HealthCheckResponseBuilder createResponseBuilder() {
		return new JemoHealthCheckResponseBuilder();
	}

}
