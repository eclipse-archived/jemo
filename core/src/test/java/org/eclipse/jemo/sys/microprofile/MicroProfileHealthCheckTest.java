package org.eclipse.jemo.sys.microprofile;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import javax.servlet.ServletOutputStream;
import javax.servlet.WriteListener;

import org.eclipse.jemo.HttpServletRequestAdapter;
import org.eclipse.jemo.HttpServletResponseAdapter;
import org.eclipse.jemo.JemoGSMTest;
import org.eclipse.jemo.api.FixedModule;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.eclipse.microprofile.health.HealthCheckResponse.State;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MicroProfileHealthCheckTest extends JemoGSMTest {
	Lock sequential = new ReentrantLock();

	@Before
	public void setUp() throws Exception {
	    sequential.lock();
	}

	@After
	public void tearDown() throws Exception {
	    sequential.unlock();
	}
	
	/*
	 * let's define a test class which implements a a fixed module, but will expose a number of health checks
	 * some which return an up status and others which return a down status.
	 */
	public static class UpHealth implements HealthCheck {

		@Override
		public HealthCheckResponse call() {
			return HealthCheckResponse.builder().up().build();
		}
		
	}
	
	public static class DownHealth implements HealthCheck {
		@Override
		public HealthCheckResponse call() {
			return HealthCheckResponse.builder()
					.name("always-down")
					.down()
					.withData("b", false)
					.withData("l", 1)
					.withData("s", "value")
					.build();
		}
	}
	
	public static class ErrorHealth implements HealthCheck {
		
		static {
			Util.B(null, x -> { throw new Exception("error"); }); 
		}
		
		@Override
		public HealthCheckResponse call() {
			return HealthCheckResponse.builder().up().build();
		}
	}
	
	
	
	public static class TestMicroprofileHealthCheck implements FixedModule {

		@Override
		public void processFixed(String location, String instanceId) throws Throwable {
			// TODO Auto-generated method stub
			
		}
		
	}
	
	protected Map<String, Object> callHealthCheckService(int appId,double version) throws Throwable {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		jemoServer.getPluginManager().process(new HttpServletRequestAdapter() {
			@Override
			public String getServletPath() {
				return "/"+String.valueOf(appId)+"/v"+String.valueOf(version)+"/health";
			}
		}, new HttpServletResponseAdapter() {

			@Override
			public ServletOutputStream getOutputStream() throws IOException {
				// TODO Auto-generated method stub
				return new ServletOutputStream() {
					
					@Override
					public void write(int b) throws IOException {
						byteOut.write(b);
					}
					
					@Override
					public void setWriteListener(WriteListener writeListener) {}
					
					@Override
					public boolean isReady() { return true; }
				};
			}
			
		});
		return Util.fromJSONString(Map.class, Util.toString(new ByteArrayInputStream(byteOut.toByteArray())));
	}
	
	/**
	 * by specification a health check is a class located within the Jar for the application that either implements the Healthcheck interface
	 * or that is annotated with the @Health annotation. the single overall status will be UP only if all checks return the UP status otherwise
	 * it will be down.
	 * 
	 * based on the microprofile specification if we specify a health check then this should appear on the base url of the application under
	 * the default base path /health so for a jemo application the path would be /[appId]/[version]/health
	 * 
	 * @throws Throwable
	 */
	@Test
	public void testWire() throws Throwable {
		//1. we need an HTTP/HTTPS module with a multitude of defined health checks.
		uploadPlugin(41, 1.0, "TestMicroprofileHealthCheck", TestMicroprofileHealthCheck.class, UpHealth.class, ErrorHealth.class);

		//2. let's call in through our endpoint and get the JSON result back.
		Map<String, Object> wireResult = callHealthCheckService(41, 1.0);
		assertEquals(State.UP.name(), wireResult.get("status"));
		
		//3. let's upload the module again with a down state
		uploadPlugin(41, 1.0, "TestMicroprofileHealthCheck", TestMicroprofileHealthCheck.class, UpHealth.class, ErrorHealth.class, DownHealth.class);
		wireResult = callHealthCheckService(41, 1.0);
		assertEquals(State.DOWN.name(), wireResult.get("status"));
		List<Map<String,Object>> checks = (List<Map<String,Object>>)wireResult.get("checks");
		assertNotNull(checks);
		Map<String,Object> alwaysDownResult = checks.stream()
				.filter(check -> check.get("name").equals("always-down"))
				.findFirst().orElse(null);
		assertNotNull(alwaysDownResult);
		Map<String,Object> data = (Map<String,Object>)alwaysDownResult.get("data");
		assertNotNull(data);
		assertEquals(false, data.get("b"));
		assertEquals(1, data.get("l"));
		assertEquals("value", data.get("s"));
		
		//4. upload a module with no healthchecks
		uploadPlugin(41, 1.0, "TestMicroprofileHealthCheck", TestMicroprofileHealthCheck.class);
		wireResult = callHealthCheckService(41, 1.0);
		assertNull(wireResult);
	}
}
