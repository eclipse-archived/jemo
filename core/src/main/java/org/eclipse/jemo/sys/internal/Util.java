/*
 ********************************************************************************
 * Copyright (c) 9th November 2018 Cloudreach Limited Europe
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
package org.eclipse.jemo.sys.internal;

import org.eclipse.jemo.Jemo;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1LoadBalancerIngress;
import io.kubernetes.client.models.V1Service;

import java.io.*;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.zip.CRC32;

import static java.nio.file.StandardOpenOption.CREATE;

/**
 * these are a series of utility methods used by the Jemo core.
 *
 * @author christopher stura
 */
public class Util {
    public static final ObjectMapper mapper = new ObjectMapper();
    public static final Pattern INT_PATTERN = Pattern.compile("[0-9]+");
    public static final Pattern RANGE_PATTERN = Pattern.compile("([0-9]+)-([0-9]+)");
    public static final Charset UTF8_CHARSET = Charset.forName("UTF-8");

    static {
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        TypeFactory tf = TypeFactory.defaultInstance().withClassLoader(Util.class.getClassLoader());
        mapper.setTypeFactory(tf);
    }

    public static int parse(String str) {
        int result = 0;
        try {
            result = new Double(str).intValue();
        } catch (Throwable ex) {
        }

        return result;
    }

    public static String toString(InputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        byte[] buf = new byte[8192];
        int rb = 0;
        while ((rb = in.read(buf)) != -1) {
            out.append(new String(buf, 0, rb, "UTF-8"));
        }
        return out.toString();
    }

    public static byte[] toByteArray(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        stream(out, in, true);
        return out.toByteArray();
    }

    public static String md5(String origStr) throws NoSuchAlgorithmException, UnsupportedEncodingException {
        MessageDigest md5 = MessageDigest.getInstance("MD5");
        md5.update(origStr.getBytes("UTF-8"));
        return String.format("%032x", new BigInteger(1, md5.digest()));
    }

    public static <T extends Object, K extends Object> Map<T, K> MAP(Map.Entry<T, K>... entryList) {
        Map<T, K> map = new LinkedHashMap<>();
        Arrays.asList(entryList).stream().forEach(e -> map.put(e.getKey(), e.getValue()));
        return map;
    }

    public static void stream(OutputStream out, InputStream in) throws IOException {
        stream(out, in, true);
    }

    public static void stream(OutputStream out, InputStream in, boolean close) throws IOException {
        byte[] buf = new byte[8192];
        int rb = 0;
        while ((rb = in.read(buf)) != -1) {
            out.write(buf, 0, rb);
            out.flush();
        }
        if (close) {
            in.close();
        }
    }

    public static final void log(Level logLevel, String message, Object... args) {
        try {
            Class ccClass = Class.forName("org.eclipse.jemo.Jemo");
            ccClass.getMethod("log", Level.class, String.class, Object[].class).invoke(ccClass, logLevel, message, args);
        } catch (ClassNotFoundException | NoSuchMethodException | IllegalAccessException | InvocationTargetException ex) {
        }
    }

    public static final <T extends Object> T fromJSONString(Class<T> cls, String jsonString) throws IOException {
        if (jsonString != null && !jsonString.isEmpty()) {
            try {
                return mapper.readValue(jsonString, cls);
            } catch (JsonParseException parseEx) {
                return null;
            }
        }

        return null;
    }

    public static final String toJSONString(Object obj) throws JsonProcessingException {
        return mapper.writeValueAsString(obj);
    }

    public static final void setFieldValue(Object instanceOrClass, String fieldName, Object value) {
        Util.B(null, x -> {
            Field f = (instanceOrClass instanceof Class ? (Class) instanceOrClass : instanceOrClass.getClass()).getDeclaredField(fieldName);
            f.setAccessible(true); //we are going to need to ensure that the util class is given access to introspect everything. (via module-info.java)
            f.set(instanceOrClass, value);
        });
    }

    public static final <T extends Object> T getFieldValue(Object instanceOrClass, String fieldName, Class<T> returnType) {
        return F(null, x -> {
            Field f = (instanceOrClass instanceof Class ? (Class) instanceOrClass : instanceOrClass.getClass()).getDeclaredField(fieldName);
            f.setAccessible(true);
            return returnType.cast(f.get(instanceOrClass));
        });
    }

    /**
     * this method will use reflection to run a static method on a class of choice. This method will allow you to access
     * methods regardless of their modifiers so you can run public, private or protected methods
     *
     * @param <T>        the return value of the method if retval is set to null then null will be returned.
     * @param retval     the type of the return value to be expected from the method.
     * @param className  the class name where the static method is defined.
     * @param methodName the name of the method to execute
     * @param args       the values to pass in as arguments to the method. if null values are specified then the types will not be matched
     *                   and the first method matching the number of arguments and the sequence of valued types will be used.
     * @return the return value of the method executed.
     */
    public static final <T extends Object> T runStaticMethod(Class<T> retval, String className, String methodName, Object... args) throws ClassNotFoundException, NoSuchMethodException {
        return runMethod(null, retval, className, methodName, args);
    }

    public static final <T extends Object> T runMethod(Object target, Class<T> returnType, String methodName, Object... args) throws ClassNotFoundException, NoSuchMethodException {
        return runMethod(target, returnType, target.getClass().getName(), methodName, args);
    }

    private static final <T extends Object> T runMethod(Object target, Class<T> returnType, String className, String methodName, Object... args) throws ClassNotFoundException, NoSuchMethodException {
        Class cls = Class.forName(className);
        Method method = Arrays.asList(cls.getDeclaredMethods()).stream()
                .filter(m -> m.getName().equals(methodName) && args != null ? m.getParameterCount() == args.length : args.length == 0)
                .filter(m -> {
                    if (args != null) {
                        Class[] paramTypes = m.getParameterTypes();
                        for (int i = 0; i < args.length; i++) {
                            if (!(args[i] == null || paramTypes[i].isAssignableFrom(args[i].getClass()))) {
                                return false;
                            }
                        }
                    }

                    return true;
                }).findFirst().orElseThrow(() -> new NoSuchMethodException(className + ":" + methodName));

        try {
            Object r = method.invoke(target == null ? cls : target, args);
            if (returnType != null) {
                return returnType.cast(r);
            }

            return null;
        } catch (IllegalAccessException | InvocationTargetException accEx) {
            throw new NoSuchMethodException(className + ":" + methodName + " because of: " + accEx.getClass().getSimpleName() + " = " + accEx.getMessage());
        }
    }

    public static <T, P> T F(P param, ManagedFunctionWithException<P, T> function) {
        try {
            return function.apply(param);
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <P> boolean A(P param, ManagedAcceptor<P> acceptor) {
        try {
            return acceptor.accept(param);
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }

    public static <P> void B(P param, ManagedConsumer<P> consumer) {
        try {
            consumer.accept(param);
        } catch (Throwable ex) {
            throw new RuntimeException(ex);
        }
    }
    
    public static <T, P> T I(P param, ManagedFunctionWithException<P, T> consumer) {
    	try {
    		return consumer.apply(param);
    	} catch (Throwable ex) {}
    	
    	return null;
    }

    public static final String getTimeString(long elapsedTime) {
        long hours = TimeUnit.HOURS.convert(elapsedTime, TimeUnit.MILLISECONDS);
        elapsedTime = elapsedTime - (TimeUnit.MILLISECONDS.convert(hours, TimeUnit.HOURS));
        long minutes = TimeUnit.MINUTES.convert(elapsedTime, TimeUnit.MILLISECONDS);
        elapsedTime = elapsedTime - (TimeUnit.MILLISECONDS.convert(minutes, TimeUnit.MINUTES));
        long seconds = TimeUnit.SECONDS.convert(elapsedTime, TimeUnit.MILLISECONDS);

        return String.format("%d Hours %d Minutes %d Seconds", hours, minutes, seconds);
    }

    public static final boolean deleteDirectory(File dir) {
				if(dir != null) {
					File[] fileList = dir.listFiles();
					if(fileList != null && fileList.length > 0) {
						Arrays.asList(fileList).stream().forEach(f -> {
								if (f.isDirectory()) {
										deleteDirectory(f);
								} else {
										f.delete();
								}
						});
					}
					return dir.delete();
				}
				
				return true;
    }

    public static Set<Integer> parseIntegerRangeDefinition(final String rangeDefinition) {
        return rangeDefinition == null ? new HashSet<>() : Arrays.asList(rangeDefinition.split(","))
                .stream().map(p -> {
                    Matcher intMatcher = INT_PATTERN.matcher(p);
                    Matcher rangeMatcher = RANGE_PATTERN.matcher(p);
                    Set<Integer> idRange = new HashSet<>();
                    if (rangeMatcher.matches()) {
                        rangeMatcher.find(0);
                        int start = Integer.parseInt(rangeMatcher.group(1));
                        int end = Integer.parseInt(rangeMatcher.group(2));
                        idRange.addAll(IntStream.rangeClosed(start, end).mapToObj(Integer::new).collect(Collectors.toList()));
                    } else if (intMatcher.matches()) {
                        idRange.add(Integer.parseInt(p));
                    }
                    return idRange;
                }).flatMap(rs -> rs.stream()).collect(Collectors.toSet());
    }

    public static final <T extends Object> List<T> LIST(List<T> list) {
        return list != null ? list : new ArrayList<>();
    }

    public static void createJar(OutputStream out, Class... classList) throws Throwable {
        try (JarOutputStream jarOut = new JarOutputStream(out)) {
            for (Class cls : classList) {
                addClassToJar(jarOut, cls);
            }
            jarOut.flush();
        }
    }
    
    public static void createJar(OutputStream out, org.eclipse.jemo.sys.internal.JarEntry... entries) throws Throwable {
    	try (JarOutputStream jarOut = new JarOutputStream(out)) {
            for (org.eclipse.jemo.sys.internal.JarEntry entry : entries) {
                addEntryToJar(jarOut, entry.getEntryName(), toByteArray(entry.getEntryData()));
            }
            jarOut.flush();
        }
    }

    private static void addClassToJar(JarOutputStream jarOut, Class cls) throws Throwable {
        ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
        try (InputStream clsIn = cls.getResourceAsStream("/" + cls.getName().replace('.', '/') + ".class")) {
            byte[] buf = new byte[8192];
            int rb = 0;
            while ((rb = clsIn.read(buf)) != -1) {
                byteOut.write(buf, 0, rb);
            }
        }
        byte[] clsBytes = byteOut.toByteArray();
        addEntryToJar(jarOut, cls.getName().replace('.', '/') + ".class", clsBytes);
    }
    
    private static void addEntryToJar(JarOutputStream jarOut, String entryName, byte[] entryBytes) throws IOException {
    	JarEntry entry = new JarEntry(entryName);
        entry.setSize(entryBytes.length);
        entry.setTime(System.currentTimeMillis());
        jarOut.putNextEntry(entry);
        jarOut.write(entryBytes);
        jarOut.closeEntry();
    }

    public static long crc(byte[] bytes) {
        final CRC32 crc = new CRC32();
        crc.update(bytes);
        return crc.getValue();
    }

    /**
     * Returns the path of the file or directory under the home directory that matches the specified relative path.
     *
     * @param relativePath the relative path under the home directory
     * @return the path
     */
    public static Path pathUnderHomdeDir(String relativePath) {
      return Paths.get(System.getProperty("user.home")).resolve(relativePath);
    }

    /**
     * Utility method used for templating. The template is read from the file with the specified file name under the specified source directory,
     * then the specified function is applied and the derived string is written to a file with the same name under the specified target directory.
     *
     * @param sourceDir the source directory
     * @param targetDir the target directory
     * @param fileName  the file name
     * @param clazz     the class of the caller instance. Needed to find the source file by using the classloader that loaded the class
     * @param func      the function to apply
     * @throws IOException
     */
    public static void applyTemplate(String sourceDir, Path targetDir, String fileName, Class clazz, Function<String, String> func) throws IOException {
        final InputStream in = clazz.getResourceAsStream("/" + sourceDir + fileName);
        final String content = func.apply(new BufferedReader(new InputStreamReader(in)).lines()
                .collect(Collectors.joining("\n")));
        Files.write(targetDir.resolve(fileName), content.getBytes(), CREATE);
    }

    /**
     * Copies the file with the specified name from the specified source directory to the target directory
     *
     * @param sourceDir     the source directory
     * @param targetDirPath the target directory path
     * @param fileName      the filename
     * @param clazz         the class of the caller instance. Needed to find the source file by using the classloader that loaded the class
     * @throws IOException
     */
    public static void copy(String sourceDir, Path targetDirPath, String fileName, Class clazz) throws IOException {
        applyTemplate(sourceDir, targetDirPath, fileName, clazz, x -> x);
    }

    /**
     * Checks if the specified command is in the path
     *
     * @return true if the specified command is in the path, false otherwise
     */
    public static boolean isCommandInstalled(String command) {
        try {
            runProcess(null, command);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Runs the specified command in a separate process and waits for the process to finish.
     * Writes to the specified string builder anything streamed to the stdin and stderror streams of the process.
     *
     * @param builder the stringbuilder object to write anything streamed to the stdin and stderror streams of the process
     * @param command the command to run
     * @return an array of strings, the first corresponding to stdin and the second to stderror of the process
     * @throws IOException
     */
    public static String[] runProcess(StringBuilder builder, String command) throws IOException {
        System.out.println("Run: \n" + command);
        final Process process = Runtime.getRuntime().exec(command);
        return waitAndMonitorProcess(builder, process);
    }

    /**
     * Runs the specified command in a separate process and waits for the process to finish.
     * Writes to the specified string builder anything streamed to the stdin and stderror streams of the process.
     *
     * @param builder the stringbuilder object to write anything streamed to the stdin and stderror streams of the process
     * @param command the command to run
     * @return an array of strings, the first corresponding to stdin and the second to stderror of the process
     * @throws IOException
     */
    public static String[] runProcess(StringBuilder builder, String[] command) throws IOException {
        System.out.println("Run: \n" + String.join(" ", command));
        final Process process = Runtime.getRuntime().exec(command);
        return waitAndMonitorProcess(builder, process);
    }

    /**
     * Attempts to the specified parameter firstly as a JVM parameter and if not found as an environmental variable.
     * If not found, returns the specified default value
     *
     * @param name         the parameter name
     * @param defaultValue the default value to return if the parameter is not found
     * @return the parameter value
     */
    public static String readParameterFromJvmOrEnv(String name, String defaultValue) {
        if (System.getProperty(name) != null) {
            return System.getProperty(name);
        } else if (System.getenv(name) != null) {
            return System.getenv(name);
        } else {
            return defaultValue;
        }
    }

    public static String readParameterFromJvmOrEnv(String name) {
        return readParameterFromJvmOrEnv(name, null);
    }

    public static String getLoadBalancerUrl(CoreV1Api coreV1Api) throws ApiException {
        long start = System.currentTimeMillis();
        long duration;
        V1Service createdService;
        do {
            createdService = coreV1Api.readNamespacedService("jemo", "default", "true", null, null);
            duration = (System.currentTimeMillis() - start) / 60_000;
        } while (duration < 3 && isLoadBalancerUrlCreated(createdService));

        final V1LoadBalancerIngress loadBalancerIngress = createdService.getStatus().getLoadBalancer().getIngress().get(0);
        return "http://" + (loadBalancerIngress.getIp() == null ? loadBalancerIngress.getHostname() : loadBalancerIngress.getIp());
    }

    private static boolean isLoadBalancerUrlCreated(V1Service createdService) {
        final List<V1LoadBalancerIngress> ingresses = createdService.getStatus().getLoadBalancer().getIngress();
        return ingresses == null || (ingresses.get(0).getHostname() == null && ingresses.get(0).getIp() == null);
    }

    private static String[] waitAndMonitorProcess(StringBuilder builder, Process process) throws IOException {
        final StringBuilder output = new StringBuilder();
        final StringBuilder error = new StringBuilder();
        try (final BufferedReader stdInput = new BufferedReader(new InputStreamReader(process.getInputStream()));
             final BufferedReader stdError = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
            String line;
            while ((line = stdInput.readLine()) != null) {
                if (builder != null) {
                    builder.append(line);
                    builder.append('\n');
                }
                output.append(line);
                output.append('\n');
                System.out.println(line);
            }
            while ((line = stdError.readLine()) != null) {
                if (builder != null) {
                    builder.append(line);
                    builder.append('\n');
                }
                error.append(line);
                error.append('\n');
                System.out.println(line);
            }
        }

        try {
            process.waitFor();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        process.destroy();
        return new String[]{output.toString(), error.toString()};
    }

    /**
     * Reads the properties declared on the 'jemo.properties' file
     *
     * @return the properties object
     */
    public static Properties readPropertiesFile() {
        if (!Files.exists(propertiesFilePath())) {
            return null;
        }

        Properties properties = null;
        try (FileInputStream stream = new FileInputStream(propertiesFilePath().toFile())) {
            properties = new Properties();
            properties.load(stream);
        } catch (IOException e) {
            Jemo.log(Level.FINE, "Properties file not found.");
        }
        return properties;
    }

    /**
     * Stores the specified properties to the 'jemo.properties' file
     *
     * @param properties the properties object to store
     */
    public static void storePropertiesFile(Properties properties) {
        try {
            properties.store(new FileOutputStream(propertiesFilePath().toString()), null);
        } catch (IOException e) {
            Jemo.log(Level.FINE, "Failed to write on properties file: "+ propertiesFilePath());
        }
    }

    private static Path propertiesFilePath() {
        return Paths.get(System.getProperty("user.home")).resolve("jemo.properties");
    }

    /**
     * Adds or updates a property in the jemo.properties file
     *
     * @param key the property key
     * @param value the property value
     */
    public static void addJemoProperty(String key, String value) {
        Properties propertiesFromFile = readPropertiesFile();
        if (propertiesFromFile == null) {
            propertiesFromFile = new Properties();
        }
        propertiesFromFile.setProperty(key, value);
        storePropertiesFile(propertiesFromFile);
    }

    public static byte[] readAllBytes(InputStream inputStream) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int nRead;
        byte[] data = new byte[16384];
        while ((nRead = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, nRead);
        }
        return buffer.toByteArray();
    }

    /**
     * Attempts to read a property set as JVM parameter or environmental variable.
     * If not found, it looks at the specified properties object and if not found it returns the specified default value.
     *
     * @param propertyName the property name
     * @param properties the properties map
     * @param defaultValue the default value to be return if the property is not found anywhere
     * @return the property value
     */
    public static String readProperty(String propertyName, Properties properties, String defaultValue) {
        final String value = readParameterFromJvmOrEnv(propertyName);
        if (value != null) {
            return value;
        }

        return properties == null || properties.getProperty(propertyName) == null ? defaultValue : properties.getProperty(propertyName);
    }

}
