package org.eclipse.jemo.internal.model;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.*;
import java.util.*;

/**
 * @author Yannis Theocharis
 */
public class CloudRuntimeImplLoader {
    private static final String DEFAULT_PROVIDER = "com.baeldung.rate.spi.YahooFinanceCloudRuntimeProvider";

    public static List<CloudRuntimeProvider> providers() {
        Collection<URL> urlList = new ArrayList<>();
        URL resourceAsStream = CloudRuntimeImplLoader.class.getClassLoader().getResource("runtime-jars/");
        Path pluginsDir = Paths.get("runtime-jars");

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
        return provider(DEFAULT_PROVIDER);
    }

    public static CloudRuntimeProvider provider(String providerName) {
        Optional<CloudRuntimeProvider> provider = providers().stream()
                .filter(p -> p.getClass().getName().equals(providerName))
                .findFirst();
        if (provider.isPresent()) {
            return provider.get();
        } else {
            // TODO: Download the jar from some predefined directory
            return null;
        }
    }
}
