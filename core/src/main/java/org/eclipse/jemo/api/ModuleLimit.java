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
package org.eclipse.jemo.api;

import java.util.concurrent.TimeUnit;

/**
 *
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class ModuleLimit {
	private static final ModuleLimit DEFAULT_INSTANCE = ModuleLimit.newInstance().build();
	
	protected int maxActiveBatchesPerInstance;
	protected int maxActiveBatchesPerLocation;
	protected int maxActiveBatchesPerGSM;
	protected int maxActiveEventsPerInstance;
	protected int maxActiveEventsPerLocation;
	protected int maxActiveEventsPerGSM;
	protected int maxActiveFixedPerInstance;
	protected int maxActiveFixedPerLocation;
	protected int maxActiveFixedPerGSM;
	protected Frequency batchFrequency;
	protected Frequency eventFrequency;
	protected String[] batchLocations;
	protected String[] eventLocations;
	protected String[] fixedLocations;
	
	private ModuleLimit(int maxActiveBatchesPerInstance,int maxActiveBatchesPerLocation,int maxActiveBatchesPerGSM,
		int maxActiveEventsPerInstance,int maxActiveEventsPerLocation,int maxActiveEventsPerGSM,
		int maxActiveFixedPerInstance,int maxActiveFixedPerLocation,int maxActiveFixedPerGSM,
		Frequency batchFrequency,Frequency eventFrequency,String[] batchLocations,String[] eventLocations, String[] fixedLocations) {
		this.maxActiveBatchesPerGSM = maxActiveBatchesPerGSM;
		this.maxActiveBatchesPerInstance = maxActiveBatchesPerInstance;
		this.maxActiveBatchesPerLocation = maxActiveBatchesPerLocation;
		this.maxActiveEventsPerGSM = maxActiveEventsPerGSM;
		this.maxActiveEventsPerInstance = maxActiveEventsPerInstance;
		this.maxActiveEventsPerLocation = maxActiveEventsPerLocation;
		this.maxActiveFixedPerInstance = maxActiveFixedPerInstance;
		this.maxActiveFixedPerLocation = maxActiveFixedPerLocation;
		this.maxActiveFixedPerGSM = maxActiveFixedPerGSM;
		this.batchFrequency = batchFrequency;
		this.eventFrequency = eventFrequency;
		this.batchLocations = batchLocations;
		this.eventLocations = eventLocations;
		this.fixedLocations = fixedLocations;
	}
	
	/**
	 * this constructor will create a new object from the default instance.
	 */
	protected ModuleLimit() {
		this(DEFAULT_INSTANCE.getMaxActiveBatchesPerInstance(), DEFAULT_INSTANCE.getMaxActiveBatchesPerLocation(), DEFAULT_INSTANCE.getMaxActiveBatchesPerGSM(),
				 DEFAULT_INSTANCE.getMaxActiveEventsPerInstance(), DEFAULT_INSTANCE.getMaxActiveEventsPerLocation(), DEFAULT_INSTANCE.getMaxActiveEventsPerGSM(),
				 DEFAULT_INSTANCE.getMaxActiveFixedPerInstance(), DEFAULT_INSTANCE.getMaxActiveFixedPerLocation(), DEFAULT_INSTANCE.getMaxActiveFixedPerGSM(),
				 DEFAULT_INSTANCE.getBatchFrequency(), DEFAULT_INSTANCE.getEventFrequency(), DEFAULT_INSTANCE.getBatchLocations(), DEFAULT_INSTANCE.getEventLocations(),
				 DEFAULT_INSTANCE.getFixedLocations());
	}

	public int getMaxActiveBatchesPerInstance() {
		return maxActiveBatchesPerInstance;
	}

	public int getMaxActiveBatchesPerLocation() {
		return maxActiveBatchesPerLocation;
	}

	public int getMaxActiveBatchesPerGSM() {
		return maxActiveBatchesPerGSM;
	}

	public int getMaxActiveEventsPerInstance() {
		return maxActiveEventsPerInstance;
	}

	public int getMaxActiveEventsPerLocation() {
		return maxActiveEventsPerLocation;
	}

	public int getMaxActiveEventsPerGSM() {
		return maxActiveEventsPerGSM;
	}

	public int getMaxActiveFixedPerInstance() {
		return maxActiveFixedPerInstance;
	}

	public int getMaxActiveFixedPerLocation() {
		return maxActiveFixedPerLocation;
	}

	public int getMaxActiveFixedPerGSM() {
		return maxActiveFixedPerGSM;
	}

	public Frequency getBatchFrequency() {
		return batchFrequency;
	}

	public Frequency getEventFrequency() {
		return eventFrequency;
	}

	public String[] getBatchLocations() {
		return batchLocations;
	}

	public String[] getEventLocations() {
		return eventLocations;
	}

	public String[] getFixedLocations() {
		return fixedLocations;
	}
	
	public static Builder newInstance() {
		return new Builder();
	}
	
	public static ModuleLimit defaultInstance() {
		return DEFAULT_INSTANCE;
	}
	
	public static class Builder {
		private int maxActiveBatchesPerInstance = -1;
		private int maxActiveBatchesPerLocation = 1; //by default we have this maximum per location
		private int maxActiveBatchesPerGSM = -1;
		private int maxActiveEventsPerInstance = -1;
		private int maxActiveEventsPerLocation = -1;
		private int maxActiveEventsPerGSM = -1;
		private int maxActiveFixedPerInstance = 1;
		private int maxActiveFixedPerLocation = 1;
		private int maxActiveFixedPerGSM = 1;
		private Frequency batchFrequency = Frequency.of(TimeUnit.MINUTES, 1);
		private Frequency eventFrequency = null;
		private String[] batchLocations = null;
		private String[] eventLocations = null;
		private String[] fixedLocations = null;

		public Builder setMaxActiveBatchesPerInstance(int maxActiveBatchesPerInstance) {
			this.maxActiveBatchesPerInstance = maxActiveBatchesPerInstance;
			this.maxActiveBatchesPerGSM = -1;
			this.maxActiveBatchesPerLocation = -1;
			return this;
		}

		public Builder setMaxActiveBatchesPerLocation(int maxActiveBatchesPerLocation) {
			this.maxActiveBatchesPerLocation = maxActiveBatchesPerLocation;
			this.maxActiveBatchesPerInstance = -1;
			this.maxActiveBatchesPerGSM = -1;
			return this;
		}

		public Builder setMaxActiveBatchesPerGSM(int maxActiveBatchesPerGSM) {
			this.maxActiveBatchesPerGSM = maxActiveBatchesPerGSM;
			this.maxActiveBatchesPerInstance = -1;
			this.maxActiveBatchesPerLocation = -1;
			return this;
		}

		public Builder setMaxActiveEventsPerInstance(int maxActiveEventsPerInstance) {
			this.maxActiveEventsPerInstance = maxActiveEventsPerInstance;
			return this;
		}

		public Builder setMaxActiveEventsPerLocation(int maxActiveEventsPerLocation) {
			this.maxActiveEventsPerLocation = maxActiveEventsPerLocation;
			return this;
		}

		public Builder setMaxActiveEventsPerGSM(int maxActiveEventsPerGSM) {
			this.maxActiveEventsPerGSM = maxActiveEventsPerGSM;
			return this;
		}

		/**
		 * this will tell the engine that there will be a maximum number of fixed processes in each instance
		 * this can be used to throttle the use of the network capacity on the type nodes of a cluster.
		 * 
		 * Please note that the engine will always try and maintain this number of running instances and restart them
		 * when needed.
		 * 
		 * @param maxActiveFixedPerInstance the maximum number of instances running on each instance.
		 * @return 
		 */
		public Builder setMaxActiveFixedPerInstance(int maxActiveFixedPerInstance) {
			this.maxActiveFixedPerInstance = maxActiveFixedPerInstance;
			this.maxActiveFixedPerGSM = -1;
			this.maxActiveFixedPerLocation = -1;
			return this;
		}

		/**
		 * this will tell the engine that there will be a maximum number of fixed instances in each location
		 * by setting this property you will automatically tell the engine to ignore the total number per GSM
		 * and per instance.
		 * 
		 * @param maxActiveFixedPerLocation
		 * @return a reference to the builder.
		 */
		public Builder setMaxActiveFixedPerLocation(int maxActiveFixedPerLocation) {
			this.maxActiveFixedPerLocation = maxActiveFixedPerLocation;
			this.maxActiveFixedPerGSM = -1;
			this.maxActiveFixedPerInstance = -1;
			return this;
		}

		public Builder setMaxActiveFixedPerGSM(int maxActiveFixedPerGSM) {
			this.maxActiveFixedPerGSM = maxActiveFixedPerGSM;
			this.maxActiveFixedPerLocation = -1;
			this.maxActiveFixedPerInstance = -1;
			return this;
		}

		public Builder setBatchFrequency(Frequency batchFrequency) {
			this.batchFrequency = batchFrequency;
			return this;
		}

		public Builder setEventFrequency(Frequency eventFrequency) {
			this.eventFrequency = eventFrequency;
			return this;
		}

		public Builder setBatchLocations(String... batchLocations) {
			this.batchLocations = batchLocations;
			return this;
		}

		public Builder setEventLocations(String... eventLocations) {
			this.eventLocations = eventLocations;
			return this;
		}

		public Builder setFixedLocations(String... fixedLocations) {
			this.fixedLocations = fixedLocations;
			return this;
		}
		
		public ModuleLimit build() {
			return new ModuleLimit(maxActiveBatchesPerInstance, maxActiveBatchesPerLocation, maxActiveBatchesPerGSM, maxActiveEventsPerInstance, maxActiveEventsPerLocation, maxActiveEventsPerGSM,
				maxActiveFixedPerInstance, maxActiveFixedPerLocation, maxActiveFixedPerGSM, batchFrequency, eventFrequency, batchLocations, eventLocations, fixedLocations);
		}
	}
}
