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

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.StreamSupport;

import javax.annotation.Priority;

import org.eclipse.jemo.api.Module;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

public class JemoConfig implements Config {

	private final AtomicReference<JemoConfigSource> configSource;
	private final AtomicReference<MicroProfileConfigSource> mpConfigSource;
	private final CopyOnWriteArrayList<ConfigSource> otherSources = new CopyOnWriteArrayList<>();
	private final CopyOnWriteArrayList<Converter> dataConverters = new CopyOnWriteArrayList<>();
	
	protected JemoConfig() {
		configSource = new AtomicReference<>(null);
		mpConfigSource = new AtomicReference<>(null);
	}
	
	public JemoConfig(Map<String,String> jemoConfig, MicroProfileConfigSource mpConfigSource) {
		this.configSource = new AtomicReference<>(new JemoConfigSource(jemoConfig));
		this.mpConfigSource = new AtomicReference<>(mpConfigSource);
	}
	
	@Override
	public <T> T getValue(String propertyName, Class<T> propertyType) {
		return getOptionalValue(propertyName, propertyType).get();
	}

	@Override
	public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType) {
		return getOptionalValue(propertyName, propertyType, null);
	}
	
	public <T> Optional<T> getOptionalValue(String propertyName, Class<T> propertyType, String defaultValue) {
		final String configVal = StreamSupport.stream(getConfigSources().spliterator(), false)
				.filter(src -> src.getPropertyNames().contains(propertyName))
				.limit(1)
				.map(src -> src.getValue(propertyName))
				.findFirst().orElse(defaultValue);

		if(configVal == null) {
			return Optional.empty();
		} else {
			if(String.class.isAssignableFrom(propertyType)) {
				return Optional.of(propertyType.cast(configVal));
			} else {
				//we need to check if a data converter exists for this class type
				Converter c = dataConverters.stream()
						.filter(dc -> Arrays.asList(dc.getClass().getGenericInterfaces())
									.stream()
									.filter(i -> i instanceof ParameterizedType)
									.map(i -> (ParameterizedType)i)
									.filter(i -> Converter.class.isAssignableFrom(((Class)i.getRawType())))
									.filter(i -> i.getActualTypeArguments().length == 1)
									.map(i -> i.getActualTypeArguments()[0])
									.filter(t -> t instanceof Class)
									.map(t -> (Class)t)
									.anyMatch(cls -> cls.isAssignableFrom(propertyType))
								)
						.sorted((c1,c2) -> {
							Integer p1 = null;
							Integer p2 = null;
							
							if(c1 instanceof TypedConverter) {
								p1 = TypedConverter.class.cast(c1).priority;
								p2 = TypedConverter.class.cast(c2).priority;
							} else {
								Priority pc1 = c1.getClass().getAnnotation(Priority.class);
								Priority pc2 = c2.getClass().getAnnotation(Priority.class);
								
								p1 = Integer.valueOf(pc1 == null ? 100 : pc1.value());
								p2 = Integer.valueOf(pc2 == null ? 100 : pc2.value());
							}
							
							return p1.compareTo(p2);
						})
						.findFirst().orElse(null);
				if(c == null) {
					try {
						return Optional.of(Util.fromJSONString(propertyType, configVal));
					}catch(Throwable ex) {
						throw new IllegalArgumentException(
								String.format("The JSON String %s cannot be convereted to the Java Type %s",configVal, propertyType.getName())
								, ex);
					}
				} else {
					return Optional.of(propertyType.cast(c.convert(configVal)));
				}
			}
		}
	}

	@Override
	public Iterable<String> getPropertyNames() {
		return configSource.get().getPropertyNames();
	}
	
	public void setConfigSource(JemoConfigSource configSource) {
		this.configSource.set(configSource);
	}
	
	public void setMicroProfileSource(MicroProfileConfigSource mpConfigSource) {
		this.mpConfigSource.set(mpConfigSource);
	}
	
	public void addConfigSource(ConfigSource otherSource) {
		this.otherSources.add(otherSource);
	}
	
	public void addDataConverter(Converter converter) {
		this.dataConverters.add(converter);
	}

	@Override
	public Iterable<ConfigSource> getConfigSources() {
		ArrayList<ConfigSource> result = new ArrayList<>();
		if(mpConfigSource.get() != null) {
			result.add(mpConfigSource.get());
		}
		if(configSource.get() != null) {
			result.add(configSource.get());
		}
		result.addAll(otherSources);
		result.sort((s1,s2) -> Integer.valueOf(s1.getOrdinal()).compareTo(Integer.valueOf(s2.getOrdinal())));
		return result;
	}
	
	protected static class TypedConverter implements Converter<Object> {
		final Class type;
		final int priority;
		final Converter converter;
		
		TypedConverter(Class type, int priority, Converter converter) {
			this.type = type;
			this.priority = priority;
			this.converter = converter;
		}

		@Override
		public Object convert(String value) {
			return converter.convert(value);
		}
	}
	
	public static String getConfigKey(ConfigProperty cfg,Module jemoModule,Field configField) {
		return cfg.name().isEmpty() ? jemoModule.getClass().getName()+"."+configField.getName() : cfg.name();
	}
}
