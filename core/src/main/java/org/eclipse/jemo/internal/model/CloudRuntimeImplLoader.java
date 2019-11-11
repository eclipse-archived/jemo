package org.eclipse.jemo.internal.model;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;
import java.util.logging.Level;

import static org.eclipse.jemo.internal.model.CloudProvider.MEMORY;

/**
 * @author Yannis Theocharis
 */
public class CloudRuntimeImplLoader {
    private static final String RUNTIME_JARS_FOLDER = "runtime-jars/";

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
        if (props == null) {
            loadProperties();
        }

        String name = cloudProvider.getName().toLowerCase();
        String url = props.getProperty("jemo.runtime." + name + ".url");
        String fileName = RUNTIME_JARS_FOLDER + name + ".jar";
        try {
            InputStream in = new URL(url).openStream();
            Files.copy(in, Paths.get(fileName), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException("Failed to download the runtime implementation for " + cloudProvider.getName() +
                    " from: " + url + ". Please review the runtime_impl.properties");
        }
    }

    private static Properties props;

    private static void loadProperties() {
        props = new Properties();
        String propFileName = "runtime_impl.properties";

        try (InputStream inputStream = CloudRuntimeImplLoader.class.getClassLoader().getResourceAsStream(propFileName)) {
            props.load(inputStream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
