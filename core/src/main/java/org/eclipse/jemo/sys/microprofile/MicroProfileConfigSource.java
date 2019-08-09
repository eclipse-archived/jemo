package org.eclipse.jemo.sys.microprofile;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.config.spi.ConfigSource;

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
