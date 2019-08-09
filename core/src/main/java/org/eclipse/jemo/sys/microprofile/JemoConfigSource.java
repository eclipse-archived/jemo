/*
********************************************************************************
* Copyright (c) 5th August 2019
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

package org.eclipse.jemo.sys.microprofile;

import java.util.HashMap;
import java.util.Map;

import org.eclipse.microprofile.config.spi.ConfigSource;

public class JemoConfigSource implements ConfigSource {

	private final Map<String,String> appConfig = new HashMap<>();
	
	public JemoConfigSource(Map<String,String> appConfig) {
		this.appConfig.putAll(appConfig);
	}
	
	@Override
	public Map<String, String> getProperties() {
		return this.appConfig;
	}

	@Override
	public String getValue(String propertyName) {
		return getProperties().get(propertyName);
	}

	@Override
	public String getName() {
		return getClass().getSimpleName();
	}

	@Override
	public int getOrdinal() {
		return ConfigSource.DEFAULT_ORDINAL;
	}
}
