package org.eclipse.jemo.sys.microprofile;

import java.util.Arrays;
import java.util.ServiceLoader;
import java.util.stream.Stream;

import javax.annotation.Priority;

import org.eclipse.jemo.internal.model.CloudProvider;
import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;

public class JemoConfigBuilder implements ConfigBuilder {
	
	private JemoConfig config = new JemoConfig();
	private JemoClassLoader classLoader;

	public JemoConfigBuilder(JemoClassLoader classLoader) {
		this.classLoader = classLoader;
	}
	/**
     * Add the default config sources appearing on the builder's classpath
     * including:
     * <ol>
     * <li>System properties - excluded as they would violate the Jemo security policy (Jemo uses a shared JVM)</li>
     * <li>Environment properties - excluded as they would violate the Jemo security policy (Jemo users shared hardware VM/Container)</li>
     * <li>/META-INF/microprofile-config.properties - valid and should be loaded from the current class loader if present.</li>
     * </ol>
     *
     * @return the ConfigBuilder with the default config sources
     */
	@Override
	public ConfigBuilder addDefaultSources() {
		this.config.setConfigSource(new JemoConfigSource(CloudProvider.getInstance().getRuntime().getModuleConfiguration(classLoader.getApplicationId())));
		this.config.setMicroProfileSource(new MicroProfileConfigSource(classLoader));
		return this;
	}

	@Override
	public ConfigBuilder addDiscoveredSources() {
		//here the service loader pattern is specified however the Jemo class loader gives us a better way to discover classes
		//of a specified type so we will use that instead and the effect will be the same.
		classLoader.getClassList().stream()
			.map(clsName -> Util.I(null, x -> Class.forName(clsName, false, classLoader)))
			.filter(cls -> ConfigSource.class.isAssignableFrom(cls))
			.flatMap(cls -> Stream.of(cls.getConstructors()))
			.filter(cstr -> cstr.getParameterCount() == 0)
			.map(cstr -> Util.I(null, x -> (ConfigSource)cstr.newInstance()))
			.filter(cfgSrc -> cfgSrc != null)
			.forEach(cfgSrc -> this.config.addConfigSource(cfgSrc));
		
		return this;
	}

	@Override
	public ConfigBuilder addDiscoveredConverters() {
		classLoader.getClassList().stream()
			.map(clsName -> Util.I(null, x -> Class.forName(clsName, false, classLoader)))
			.filter(cls -> Converter.class.isAssignableFrom(cls))
			.flatMap(cls -> Stream.of(cls.getConstructors()))
			.filter(cstr -> cstr.getParameterCount() == 0)
			.map(cstr -> Util.I(null, x -> (Converter)cstr.newInstance()))
			.filter(cnv -> cnv != null)
			.forEach(cnv -> this.config.addDataConverter(cnv));
			
		return this;
	}

	@Override
	public ConfigBuilder forClassLoader(ClassLoader loader) {
		if(loader instanceof JemoClassLoader) {
			this.classLoader = JemoClassLoader.class.cast(loader);
			this.config = new JemoConfig();
		}
		return this;
	}

	@Override
	public ConfigBuilder withSources(ConfigSource... sources) {
		Arrays.asList(sources)
			.forEach(src -> {
				if(src instanceof MicroProfileConfigSource) {
					this.config.setMicroProfileSource(MicroProfileConfigSource.class.cast(src));
				} else if(src instanceof JemoConfigSource) {
					this.config.setConfigSource(JemoConfigSource.class.cast(src));
				} else {
					this.config.addConfigSource(src);
				}
			});
		return this;
	}

	@Override
	public ConfigBuilder withConverters(Converter<?>... converters) {
		Arrays.asList(converters).forEach(cnv -> this.config.addDataConverter(cnv));
		return this;
	}

	@Override
	public <T> ConfigBuilder withConverter(Class<T> type, int priority, Converter<T> converter) {
		this.config.addDataConverter(new JemoConfig.TypedConverter(type, priority, converter));
		return this;
	}

	@Override
	public Config build() {
		return this.config;
	}

}
