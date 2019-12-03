package org.eclipse.jemo.sys.microprofile;

import java.util.Map;
import java.util.Optional;

import org.eclipse.microprofile.health.HealthCheckResponse;

public class JemoHealthCheckResponse extends HealthCheckResponse {

	final String name;
	final State state;
	final Map<String,Object> data;
	
	protected JemoHealthCheckResponse(String name, State state, Map<String,Object> data) {
		this.name = name;
		this.state = state;
		this.data = data;
	}
	
	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public State getState() {
		return this.state;
	}

	@Override
	public Optional<Map<String, Object>> getData() {
		return Optional.ofNullable(this.data);
	}

}
