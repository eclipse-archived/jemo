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
package org.eclipse.jemo.internal.model;

import org.eclipse.jemo.api.Frequency;
import org.eclipse.jemo.api.ModuleLimit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.TimeUnit;

/**
 * this class should provide all the meta-data we need to better understand what services a module
 * implementation provides.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class JemoApplicationMetaData implements SystemDBObject {
	
	public static class JemoModuleFrequency extends Frequency {
		
		public JemoModuleFrequency() {
			super();
		}

		public void setUnit(TimeUnit unit) {
			this.unit = unit;
		}

		public void setValue(long value) {
			this.value = value;
		}
		
		public static JemoModuleFrequency wrap(Frequency frequency) {
			if(frequency != null) { 
				if(frequency instanceof JemoModuleFrequency) {
					return JemoModuleFrequency.class.cast(frequency);
				} else {
					JemoModuleFrequency retval = new JemoModuleFrequency();
					retval.setUnit(frequency.getUnit());
					retval.setValue(frequency.getValue());
					return retval;
				}
			}
			return null;
		}
	}
	
	public static class JemoModuleLimits extends ModuleLimit {

		public void setMaxActiveBatchesPerInstance(int maxActiveBatchesPerInstance) {
			this.maxActiveBatchesPerInstance = maxActiveBatchesPerInstance;
		}

		public void setMaxActiveBatchesPerLocation(int maxActiveBatchesPerLocation) {
			this.maxActiveBatchesPerLocation = maxActiveBatchesPerLocation;
		}

		public void setMaxActiveBatchesPerGSM(int maxActiveBatchesPerGSM) {
			this.maxActiveBatchesPerGSM = maxActiveBatchesPerGSM;
		}

		public void setMaxActiveEventsPerInstance(int maxActiveEventsPerInstance) {
			this.maxActiveEventsPerInstance = maxActiveEventsPerInstance;
		}

		public void setMaxActiveEventsPerLocation(int maxActiveEventsPerLocation) {
			this.maxActiveEventsPerLocation = maxActiveEventsPerLocation;
		}

		public void setMaxActiveEventsPerGSM(int maxActiveEventsPerGSM) {
			this.maxActiveEventsPerGSM = maxActiveEventsPerGSM;
		}

		public void setMaxActiveFixedPerInstance(int maxActiveFixedPerInstance) {
			this.maxActiveFixedPerInstance = maxActiveFixedPerInstance;
		}

		public void setMaxActiveFixedPerLocation(int maxActiveFixedPerLocation) {
			this.maxActiveFixedPerLocation = maxActiveFixedPerLocation;
		}

		public void setMaxActiveFixedPerGSM(int maxActiveFixedPerGSM) {
			this.maxActiveFixedPerGSM = maxActiveFixedPerGSM;
		}

		public void setBatchFrequency(JemoModuleFrequency batchFrequency) {
			this.batchFrequency = batchFrequency;
		}

		public void setEventFrequency(JemoModuleFrequency eventFrequency) {
			this.eventFrequency = eventFrequency;
		}

		public void setBatchLocations(String[] batchLocations) {
			this.batchLocations = batchLocations;
		}

		public void setEventLocations(String[] eventLocations) {
			this.eventLocations = eventLocations;
		}

		public void setFixedLocations(String[] fixedLocations) {
			this.fixedLocations = fixedLocations;
		}
		
		public static JemoModuleLimits wrap(ModuleLimit limit) {
			JemoModuleLimits lim = new JemoModuleLimits();
			lim.setBatchFrequency(JemoModuleFrequency.wrap(limit.getBatchFrequency()));
			lim.setBatchLocations(limit.getBatchLocations());
			lim.setEventFrequency(JemoModuleFrequency.wrap(limit.getEventFrequency()));
			lim.setEventLocations(limit.getEventLocations());
			lim.setFixedLocations(limit.getFixedLocations());
			lim.setMaxActiveBatchesPerGSM(limit.getMaxActiveBatchesPerGSM());
			lim.setMaxActiveBatchesPerInstance(limit.getMaxActiveBatchesPerInstance());
			lim.setMaxActiveBatchesPerLocation(limit.getMaxActiveBatchesPerLocation());
			lim.setMaxActiveEventsPerGSM(limit.getMaxActiveEventsPerGSM());
			lim.setMaxActiveEventsPerInstance(limit.getMaxActiveEventsPerInstance());
			lim.setMaxActiveEventsPerLocation(limit.getMaxActiveEventsPerLocation());
			lim.setMaxActiveFixedPerGSM(limit.getMaxActiveFixedPerGSM());
			lim.setMaxActiveFixedPerInstance(limit.getMaxActiveFixedPerInstance());
			lim.setMaxActiveFixedPerLocation(limit.getMaxActiveFixedPerLocation());
			return lim;
		}
	}
	
	private double version;
	private String name;
	private String uniqueKey;
	private boolean enabled; //is this enabled or disabled.
	
	private long installDate = 0;
	private long lastUpgradeDate = 0;
	private String lastUploadedBy = null;
	private long lastUsedOn = 0; //when did we last execute the code in this application.
	
	private Map<String,String> endpoints = new ConcurrentHashMap<>();
	private Set<String> events = new ConcurrentSkipListSet<>();
	private Set<String> batches = new ConcurrentSkipListSet<>();
	private Set<String> fixed = new ConcurrentSkipListSet<>();
	private Map<String, JemoModuleLimits> limits = new ConcurrentHashMap<>();
	
	@Override
	public String getId() {
		return uniqueKey;
	}

	public void setVersion(double version) {
		this.version = version;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setId(String uniqueKey) {
		this.uniqueKey = uniqueKey;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public void setInstallDate(long installDate) {
		this.installDate = installDate;
	}

	public void setLastUpgradeDate(long lastUpgradeDate) {
		this.lastUpgradeDate = lastUpgradeDate;
	}

	public void setLastUploadedBy(String lastUploadedBy) {
		this.lastUploadedBy = lastUploadedBy;
	}

	public void setLastUsedOn(long lastUsedOn) {
		this.lastUsedOn = lastUsedOn;
	}

	public Map<String, String> getEndpoints() {
		return endpoints;
	}

	public void setEndpoints(Map<String, String> endpoints) {
		this.endpoints.clear();
		this.endpoints.putAll(endpoints);
	}

	public Set<String> getEvents() {
		return events;
	}

	public void setEvents(Set<String> events) {
		this.events.clear();
		this.events.addAll(events);
	}

	public Set<String> getBatches() {
		return batches;
	}

	public void setBatches(Set<String> batches) {
		this.batches.clear();
		this.batches.addAll(batches);
	}

	public long getLastUsedOn() {
		return lastUsedOn;
	}

	public Set<String> getFixed() {
		return fixed;
	}

	public void setFixed(Set<String> fixed) {
		this.fixed.clear();
		this.fixed.addAll(fixed);
	}

	public Map<String, JemoModuleLimits> getLimits() {
		return limits;
	}

	public void setLimits(Map<String, JemoModuleLimits> limits) {
		this.limits = limits;
	}

	public long getLastUpgradeDate() {
		return lastUpgradeDate;
	}
}
