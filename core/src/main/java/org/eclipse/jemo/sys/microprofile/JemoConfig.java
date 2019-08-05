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

import java.util.Map;
import java.util.Optional;

import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigSource;

public class JemoConfig implements Config {

	private Map<String,String> config;
	
	public JemoConfig(Map<String,String> jemoConfig) {
		this.config = jemoConfig;
	}
	
	@Override
	public <T> T getValue(String propertyName, Class<T> propertyType) {
		return getOptionalValue(propertyName, propertyType).orElseThrow();
	}

	@Override
	public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
		final String configVal = config.get(propertyName);
		if(configVal == null) {
			return Optional.empty();
		} else {
			try {
				return Optional.of(Util.fromJSONString(propertyType, configVal));
			}catch(Throwable ex) {
				throw new IllegalArgumentException(
						String.format("The JSON String %s cannot be convereted to the Java Type %s",configVal, propertyType.getName())
						, ex);
			}
		}
	}

	@Override
	public Iterable<String> getPropertyNames() {
		return config.keySet();
	}

	@Override
	public Iterable<ConfigSource> getConfigSources() {
		return null;
	}
	
}
