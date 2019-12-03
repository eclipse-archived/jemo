/*
********************************************************************************
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

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.config.spi.ConfigSource;

/**
 *
 * @author Christopher Stura "cstura@gmail.com"
 */
public class MicroProfileConfigSource implements ConfigSource {

	final static String MICROPROFILE_CONFIG = "/META-INF/microprofile-config.properties";
	final Map<String,String> config = new HashMap<>();
	
	public MicroProfileConfigSource(JemoClassLoader appClassLoader) {
		InputStream propIn = appClassLoader.getResourceAsStream(MICROPROFILE_CONFIG);
		if(propIn != null) {
			Properties props = new Properties();
			Util.B(null, x -> props.load(propIn));
			config.putAll(props.entrySet().stream()
					.collect(Collectors.toMap(e -> (String)e.getKey(), e -> (String)e.getValue())));
		}
		
	}
	
	@Override
	public Map<String, String> getProperties() {
		return config;
	}

	@Override
	public int getOrdinal() {
		return 200;
	}

	@Override
	public String getValue(String propertyName) {
		return config.get(propertyName);
	}

	@Override
	public String getName() {
		return MICROPROFILE_CONFIG;
	}
	
}
