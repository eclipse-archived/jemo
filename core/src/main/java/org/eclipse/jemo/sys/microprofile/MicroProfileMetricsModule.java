package org.eclipse.jemo.sys.microprofile;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.lang.management.ManagementFactory;
import java.util.Arrays;
import java.util.Set;

import javax.inject.Inject;
import javax.json.Json;
import javax.json.stream.JsonGenerator;
import javax.management.AttributeNotFoundException;
import javax.management.InstanceNotFoundException;
import javax.management.IntrospectionException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanException;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.Query;
import javax.management.ReflectionException;
import javax.management.openmbean.CompositeData;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jemo.AbstractJemo;
import org.eclipse.jemo.api.WebServiceModule;
import org.eclipse.jemo.sys.JemoPluginManager;
import org.eclipse.jemo.sys.internal.Util;

public class MicroProfileMetricsModule implements WebServiceModule {

	@Inject
	JemoPluginManager pluginManager;
	
	@Override
	public String getBasePath() {
		return "/metrics";
	}

	@Override
	public void process(HttpServletRequest request, HttpServletResponse response) throws Throwable {
		response.setContentType("application/json");
		try(OutputStream out = response.getOutputStream()) {
			try(JsonGenerator jsonOut = Json.createGenerator(new OutputStreamWriter(out, Util.UTF8_CHARSET))) {
				jsonOut.writeStartObject();
				writeBaseMetrics(jsonOut);
				jsonOut.writeEnd();
			}
		}
	}
	
	protected void writeBaseMetrics(JsonGenerator json) throws IOException,MBeanException,AttributeNotFoundException,ReflectionException,
		InstanceNotFoundException,MalformedObjectNameException,IntrospectionException {
		MBeanServer mbs = ManagementFactory.getPlatformMBeanServer(); 
		json.writeStartObject("base");
		CompositeData memoryData = (CompositeData)mbs.getAttribute(new ObjectName("java.lang:type=Memory"),"HeapMemoryUsage");
		json.write("memory.usedHeap", (long)memoryData.get("used"));
		json.write("memory.committedHeap", (long)memoryData.get("committed"));
		json.write("memory.maxHeap", (long)memoryData.get("max"));
		Set<ObjectName> gcNames = mbs.queryNames(new ObjectName("*:type=GarbageCollector,*"), null);
		for(ObjectName gcName : gcNames) {
			json.write("gc.total;name="+gcName.getKeyProperty("name"), (long)mbs.getAttribute(gcName, "CollectionCount"));
			json.write("gc.time;name="+gcName.getKeyProperty("name"), (long)mbs.getAttribute(gcName, "CollectionTime"));
		}
		json.write("jvm.uptime",(long)mbs.getAttribute(new ObjectName("java.lang:type=Runtime"), "Uptime"));
		json.write("thread.count", (int)mbs.getAttribute(new ObjectName("java.lang:type=Threading"), "ThreadCount"));
		json.write("thread.daemon.count", (int)mbs.getAttribute(new ObjectName("java.lang:type=Threading"), "DaemonThreadCount"));
		json.write("thread.max.count", (int)mbs.getAttribute(new ObjectName("java.lang:type=Threading"), "PeakThreadCount"));
		json.write("threadpool.activeThreads;pool=WORK_EXECUTOR", pluginManager.getActiveWorkerThreads());
		json.write("threadpool.size;pool=WORK_EXECUTOR", AbstractJemo.WORKER_THREADPOOL_MAXSIZE);
		json.write("classloader.loadedClasses.count", (int)mbs.getAttribute(new ObjectName("java.lang:type=ClassLoading"), "LoadedClassCount"));
		json.write("classloader.loadedClasses.total", (long)mbs.getAttribute(new ObjectName("java.lang:type=ClassLoading"), "TotalLoadedClassCount"));
		json.write("classloader.unloadedClasses.total", (long)mbs.getAttribute(new ObjectName("java.lang:type=ClassLoading"), "UnloadedClassCount"));
		json.write("cpu.availableProcessors", (int)mbs.getAttribute(new ObjectName("java.lang:type=OperatingSystem"), "AvailableProcessors"));
		json.write("cpu.systemLoadAverage", (double)mbs.getAttribute(new ObjectName("java.lang:type=OperatingSystem"), "SystemLoadAverage"));
		Arrays.asList(mbs.getMBeanInfo(new ObjectName("java.lang:type=OperatingSystem")).getAttributes())
			.stream()
			.filter(attr -> attr.getName().equals("ProcessCpuLoad"))
			.findFirst()
			.ifPresent(attr -> Util.B(null, x -> json.write("cpu.processCpuLoad", (double)mbs.getAttribute(new ObjectName("java.lang:type=OperatingSystem"), "ProcessCpuLoad"))));
		json.writeEnd();
		json.flush();
	}
}
