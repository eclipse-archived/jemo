package org.eclipse.jemo.internal.model;

import org.apache.commons.io.FileUtils;

import java.io.*;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;

import static org.eclipse.jemo.internal.model.CloudProvider.MEMORY;

/**
 * @author Yannis Theocharis
 */
public class CloudRuntimeImplLoader {
    private static final String RUNTIME_JARS_FOLDER = "runtime-jars/";
    private static Properties RUNTIME_IMPL_PROPS;

    public static List<CloudRuntimeProvider> providers() {
        Collection<URL> urlList = new ArrayList<>();
        URL resourceAsStream = CloudRuntimeImplLoader.class.getClassLoader().getResource(RUNTIME_JARS_FOLDER);
        Path pluginsDir = Paths.get("runtime-jars");
        if (!Files.exists(pluginsDir)) {
            try {
                Files.createDirectory(pluginsDir);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        try (DirectoryStream<Path> jars = Files.newDirectoryStream(pluginsDir, "*.jar")) {
            for (Path jar : jars) {
                urlList.add(jar.toUri().toURL());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        URL[] urls = urlList.toArray(new URL[0]);
        ClassLoader pluginClassLoader = new URLClassLoader(urls, CloudRuntimeProvider.class.getClassLoader());
        ServiceLoader<CloudRuntimeProvider> serviceLoader = ServiceLoader.load(CloudRuntimeProvider.class, pluginClassLoader);
        List<CloudRuntimeProvider> providers = new ArrayList<>();
        for (CloudRuntimeProvider provider : serviceLoader) {
            System.out.println("Found CloudRuntimeProvider: " + provider.getClass());
            providers.add(provider);
        }
        return providers;
    }

    public static CloudRuntimeProvider provider() {
        return provider(MEMORY);
    }

    public static CloudRuntimeProvider provider(CloudProvider cloudProvider) {
        Optional<CloudRuntimeProvider> provider = providers().stream()
                .filter(p -> p.getClass().getName().equals(cloudProvider.getProviderFQClassName()))
                .findFirst();
        if (provider.isPresent()) {
            return provider.get();
        } else {
            loadRuntimeImplFromWeb(cloudProvider);
            provider = providers().stream()
                    .filter(p -> p.getClass().getName().equals(cloudProvider.getProviderFQClassName()))
                    .findFirst();
            if (provider.isPresent()) {
                return provider.get();
            } else {
                throw new RuntimeException("Failed to find a runtime implementation for " + cloudProvider.getName() +
                        ". Copy the jar under the " + RUNTIME_JARS_FOLDER + " folder, or set the url where this jar is stored on runtime_impl.properties");
            }
        }
    }

    private static void loadRuntimeImplFromWeb(CloudProvider cloudProvider) {
        if (RUNTIME_IMPL_PROPS == null) {
            loadProperties();
        }

        String name = cloudProvider.getName().toLowerCase();
        String url = RUNTIME_IMPL_PROPS.getProperty("jemo.runtime." + name + ".url");
        String fileName = RUNTIME_JARS_FOLDER + name + ".jar";

        try {
            FileUtils.copyURLToFile(new URL(url), new File(fileName), 120_000, 120_000);
        }  catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static void loadProperties() {
        RUNTIME_IMPL_PROPS = new Properties();
        String propFileName = "runtime_impl.properties";

        try (InputStream inputStream = CloudRuntimeImplLoader.class.getClassLoader().getResourceAsStream(propFileName)) {
            RUNTIME_IMPL_PROPS.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
