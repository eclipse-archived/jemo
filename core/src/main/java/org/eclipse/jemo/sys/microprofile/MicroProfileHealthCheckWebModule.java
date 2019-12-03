package org.eclipse.jemo.sys.microprofile;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.ws.Holder;

import org.eclipse.jemo.api.WebServiceModule;
import org.eclipse.jemo.internal.model.JemoApplicationMetaData;
import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.State;

public class MicroProfileHealthCheckWebModule implements WebServiceModule {

	@Override
	public String getBasePath() {
		return "/health";
	}

	@Override
	public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
		JemoClassLoader appClassLoader = (JemoClassLoader)Thread.currentThread().getContextClassLoader();
		JemoApplicationMetaData appMetadata = appClassLoader.getApplicationMetadata();
		
		Holder<State> overallStatus = new Holder<>(State.UP);
		List<Map<String,Object>> result = appMetadata.getHealthchecks().stream()
				.map(checkCls -> Util.F(null, x -> {
					HealthCheck check = (HealthCheck)appClassLoader.loadClass(checkCls).getConstructor().newInstance();
					HealthCheckResponse checkResponse = check.call();
					Map<String,Object> checkResult = new HashMap<>();
					checkResult.put("name", checkResponse.getName());
					checkResult.put("status", checkResponse.getState().name());
					if(checkResponse.getData().isPresent()) {
						checkResult.put("data", checkResponse.getData().get());
					}
					if(State.DOWN.equals(checkResponse.getState())) {
						overallStatus.value = State.DOWN;
					}
					return checkResult;
				})).collect(Collectors.toList());
		
		Map<String,Object> finalResult = new HashMap<>();
		finalResult.put("status", overallStatus.value.name());
		finalResult.put("checks", result);
		
		response.setStatus(200);
		response.setContentType("application/json");
		response.setCharacterEncoding("UTF-8");
		Util.stream(response.getOutputStream(), new ByteArrayInputStream(Util.toJSONString(finalResult).getBytes(Util.UTF8_CHARSET)), true);
	}
	
}
