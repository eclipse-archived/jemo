package org.eclipse.jemo.sys.microprofile;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.State;
import org.eclipse.microprofile.health.HealthCheckResponseBuilder;

public class JemoHealthCheckResponseBuilder extends HealthCheckResponseBuilder {
	
	String name = "unnamed-healthcheck";
	State state = State.UP;
	Map<String,Object> data = null;
	
	private Map<String,Object> getData() {
		if(data == null) {
			data = new HashMap<>();
		}
		return data;
	}

	@Override
	public HealthCheckResponseBuilder name(String name) {
		this.name = name;
		return this;
	}

	@Override
	public HealthCheckResponseBuilder withData(String key, String value) {
		getData().put(key, value);
		return this;
	}

	@Override
	public HealthCheckResponseBuilder withData(String key, long value) {
		getData().put(key, value);
		return this;
	}

	@Override
	public HealthCheckResponseBuilder withData(String key, boolean value) {
		getData().put(key, value);
		return this;
	}

	@Override
	public HealthCheckResponseBuilder up() {
		return state(true);
	}

	@Override
	public HealthCheckResponseBuilder down() {
		return state(false);
	}

	@Override
	public HealthCheckResponseBuilder state(boolean up) {
		this.state = up ? State.UP : State.DOWN;
		return this;
	}

	@Override
	public HealthCheckResponse build() {
		return new JemoHealthCheckResponse(name, state, data);
	}
	
}
