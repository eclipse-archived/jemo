package org.eclipse.jemo.sys.microprofile;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.eclipse.jemo.internal.model.CloudProvider;
import org.eclipse.jemo.internal.model.ModuleConfiguration;
import org.eclipse.jemo.internal.model.ModuleConfigurationOperation;
import org.eclipse.jemo.internal.model.ModuleConfigurationParameter;
import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigBuilder;
import org.eclipse.microprofile.config.spi.ConfigProviderResolver;

public class JemoConfigProviderResolver extends ConfigProviderResolver {

	@Override
	public Config getConfig() {
		return getConfig(Thread.currentThread().getContextClassLoader());
	}
	
	private JemoClassLoader getConfigurationOwner(ClassLoader loader) {
		ClassLoader currentClassLoader = loader;
		if(currentClassLoader != null) {
			do {
				if(currentClassLoader instanceof JemoClassLoader 
						&& JemoClassLoader.class.cast(currentClassLoader).getApplicationConfiguration() != null) {
					return JemoClassLoader.class.cast(currentClassLoader);
				}
				currentClassLoader = loader.getParent();
			}while(currentClassLoader != null);
		}
		return null;
	}

	@Override
	public Config getConfig(ClassLoader loader) {
		//because we expect modules to be loaded using an JemoClassLoader somewhere
		//as we backtrack through the class loaders we should be able to find an X2ClassLoader instance.
		//this means that when we use this method we will keep looking for a parent class loader
		//until we don't find one which has a configuration object registered against it.
		JemoClassLoader appClassLoader = getConfigurationOwner(loader);
		return appClassLoader == null ? null : appClassLoader.getApplicationConfiguration();
	}

	@Override
	public ConfigBuilder getBuilder() {
		return new JemoConfigBuilder(getConfigurationOwner(Thread.currentThread().getContextClassLoader()));
	}

	@Override
	public void registerConfig(Config config, ClassLoader classLoader) {
		//here micro-profile would state that we should only allow the configuration to be set against the 
		//application identified by the class loader if an existing configuration is not already present.
		//we can enforce this in Jemo by allowing this method if there are no current configuration parameters
		//on the application. if a configuration is specified then we will return an IllegalStateException.
		Config currentConfig = getConfig(classLoader);
		if(currentConfig != null && !currentConfig.getPropertyNames().iterator().hasNext()) {
			//we can set the configuration specified (i.e. save it back to the Jemo configuration store)
			//1. we need to identify the application id
			JemoClassLoader appClassLoader = getConfigurationOwner(classLoader);
			
			
			CloudProvider.getInstance().getRuntime().setModuleConfiguration(appClassLoader.getApplicationId(), 
					toModuleConfiguration(config, ModuleConfigurationOperation.upsert));
			//we also need to notify the cluster of the configuration change. (we will throw a runtime exception if this is not possible)
			Util.B(null, (x) -> appClassLoader.getJemoServer().getPluginManager()
					.notifyConfigurationChange(appClassLoader.getApplicationId()));
			
			//we should also set the new configuration on the current class loader just in-case someone wants to use it immediately.
			applyConfigToLocalInstance(appClassLoader);
		} else {
			if(currentConfig == null) {
				throw new IllegalStateException("A configuration could not be registered against the provided class loader because "
						+ "no valid Jemo provided JemoClassLoader instance could be found in the class loader chain");
			} else {
				throw new IllegalStateException("A configuration for this application already exists. "
						+ "You can either release the current configuration by calling the releaseConfig method or you can "
						+ "use the Jemo administration user interface or web services to set new configuration parameters for this application");
			}
		}
	}

	@Override
	public void releaseConfig(Config config) {
		if(config != null) {
			JemoClassLoader appClassLoader = getConfigurationOwner(Thread.currentThread().getContextClassLoader());
			if(appClassLoader != null) {
				//we should check if the JSON string representation of the config object matches that assigned to the current application.
				final String currentConfig = toJSONString(appClassLoader.getApplicationConfiguration());
				final String refConfig = toJSONString(config);
				if(currentConfig.equalsIgnoreCase(refConfig)) {
					CloudProvider.getInstance().getRuntime().setModuleConfiguration(appClassLoader.getApplicationId(), 
							toModuleConfiguration(config, ModuleConfigurationOperation.delete));
					//we also need to notify the cluster of the configuration change. (we will throw a runtime exception if this is not possible)
					Util.B(null, (x) -> appClassLoader.getJemoServer().getPluginManager()
							.notifyConfigurationChange(appClassLoader.getApplicationId()));
					
					applyConfigToLocalInstance(appClassLoader);
				}
			}
		}
	}
	
	private void applyConfigToLocalInstance(JemoClassLoader appClassLoader) {
		//we should also set the new configuration on the current class loader just in-case someone wants to use it immediately.
		final Map<String, String> jemoConfig = appClassLoader.getJemoServer().getPluginManager().getModuleConfiguration(appClassLoader.getApplicationId());
		appClassLoader.getApplicationConfiguration().setConfigSource(new JemoConfigSource(jemoConfig));
	}
	
	private String toJSONString(Config config) {
		Map<String,String> dataMap = new TreeMap<>((x1,x2) -> x1.compareTo(x2));
		config.getPropertyNames().forEach(cfg -> dataMap.put(cfg, config.getValue(cfg, String.class)));
		return Util.F(null, x -> Util.toJSONString(dataMap));
	}
	
	private ModuleConfiguration toModuleConfiguration(Config config, ModuleConfigurationOperation operation) {
		ModuleConfiguration appConfig = new ModuleConfiguration();
		appConfig.setParameters(StreamSupport.stream(config.getPropertyNames().spliterator(), false)
				.map(cfg -> {
					final ModuleConfigurationParameter cfgItem = new ModuleConfigurationParameter();
					cfgItem.setOperation(operation);
					cfgItem.setKey(cfg);
					cfgItem.setValue(config.getValue(cfg, String.class));
					return cfgItem;
				}).collect(Collectors.toList()));
		return appConfig;
	}
}
