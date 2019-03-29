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
package org.eclipse.jemo;

import org.eclipse.jemo.sys.internal.Util;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.eclipse.jemo.api.JemoParameter.*;

/**
 * this base class will attempt to simulate an entire Jemo GSM. This means that the
 * abstract class will create a complete GSM with multiple instances of Jemo running at the same time
 * all connected to the same virtual GSM.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public abstract class JemoGSMTest extends JemoBaseTest {
	
	private static final Map<String,List<TestJemoServer>> serverGSMMap = new HashMap<>(); //this defines the locations in the cluster
	private static int nextHttpPort = 8080;
	
	static {
		registerServerInstance(new JemoGSMTest("TEST1","TEST2") {});
		System.out.println("JemoGSMTest base instance registered correctly.");
	}
	
	protected JemoGSMTest(String... locations) {
		super(true);
		System.setProperty(CLOUD.label(),"MEMORY");
		System.clearProperty(MODULE_BLACKLIST.label());
		System.clearProperty(MODULE_WHITELIST.label());

		serverGSMMap.putAll(Arrays.asList(locations).stream()
			.collect(Collectors.toMap(l -> l, l -> new ArrayList<>())));
	}
	
	protected JemoGSMTest() {}

	@Override
	public void stopJemo() {
		serverGSMMap.values().stream()
			.flatMap(srvList -> srvList.stream())
			.forEach(srv -> Util.B(null, x -> srv.stop()));
		jemoServer = null;
		serverGSMMap.clear();
	}

	@Override
	public void startJemo() {
		serverGSMMap.entrySet().stream()
			.forEach(e -> {
				List<TestJemoServer> locationCluster = Arrays.asList(new TestJemoServer("TEST_GSM_"+UUID.randomUUID().toString(), e.getKey(), nextHttpPort, null), new TestJemoServer("TEST_GSM_"+UUID.randomUUID().toString(), e.getKey(), nextHttpPort+2, null));
				nextHttpPort += 4;
				locationCluster.forEach(srv -> Util.B(null, x -> srv.start()));
				e.getValue().addAll(locationCluster);
				Util.B(null, x -> TimeUnit.SECONDS.sleep(5)); //wait 5 seconds before starting the next server
			});
		jemoServer = serverGSMMap.values().iterator().next().iterator().next();
	}
	
	/**
	 * this method will stop an instance started in the virtual cluster if the instance with the given instance id can be found.
	 * @param instanceId the id of the instance to stop
	 */
	public void stopServerByInstanceId(String instanceId) {
		serverGSMMap.values().stream()
			.flatMap(List::stream)
			.filter(srv -> srv.getINSTANCE_ID().equals(instanceId))
			.findFirst()
			.ifPresent(srv -> {
				Util.B(null, x -> srv.stop());
				serverGSMMap.get(srv.getLOCATION()).remove(srv);
				jemoServer = serverGSMMap.values().iterator().next().iterator().next();
			});
	}

	@Override
	protected void processBatch(int moduleId, double moduleVersion, String moduleClass) throws InterruptedException {
		serverGSMMap.values().stream()
			.flatMap(List::stream)
			.forEach(srv -> srv.getPluginManager().clearBatchExecutionMap());
		
		super.processBatch(moduleId, moduleVersion, moduleClass); //To change body of generated methods, choose Tools | Templates.
	}
	
	protected Set<String> instanceList() {
		return serverGSMMap.values().stream()
			.flatMap(List::stream)
			.map(TestJemoServer::getINSTANCE_ID)
			.collect(Collectors.toSet());
	}
	
	protected Set<String> instanceList(String location) {
		return serverGSMMap.values().stream()
			.flatMap(List::stream)
			.filter(srv -> srv.getLOCATION().equals(location))
			.map(TestJemoServer::getINSTANCE_ID)
			.collect(Collectors.toSet());
	}
	
	protected Set<String> locationList() {
		return serverGSMMap.keySet();
	}
	
	protected List<TestJemoServer> listServers() {
		return serverGSMMap.values().stream().flatMap(List::stream).collect(Collectors.toList());
	}
}
