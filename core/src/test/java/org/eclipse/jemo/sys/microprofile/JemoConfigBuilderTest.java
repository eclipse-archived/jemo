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

import static org.junit.Assert.*;

import java.io.ByteArrayOutputStream;
import java.util.AbstractMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.eclipse.jemo.JemoGSMTest;
import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.junit.Test;

/**
 * The purpose of this test file is to ensure that the class org.eclipse.jemo.sys.microprofile.JemoConfigBuilder
 * obtains 100% code coverage from a unit test standpoint.
 * 
 * Because this class can only be used within an Active Jemo module we will need to instantiate the server to make sure these
 * tests run correctly.
 * 
 * @author Christopher Stura "cstura@gmail.com"
 */
public class JemoConfigBuilderTest extends JemoGSMTest {

	public static class MyCustomerSource implements ConfigSource {

		private Map<String,String> cfg = Util.MAP(new AbstractMap.SimpleEntry("custom", "value"));
		
		@Override
		public Map<String, String> getProperties() {
			return cfg;
		}

		@Override
		public String getValue(String propertyName) {
			return cfg.get(propertyName);
		}

		@Override
		public String getName() {
			return "MyCustomerSource";
		}
		
	}
	
	public static class MyUnacceptableSource implements ConfigSource {

		public MyUnacceptableSource(String param1) {}
		
		@Override
		public Map<String, String> getProperties() { return null; }

		@Override
		public String getValue(String propertyName) { return null; }

		@Override
		public String getName() { return null; }
		
	}
	
	public static class ErrorThrowingSource implements ConfigSource {
		public ErrorThrowingSource() {
			throw new RuntimeException("Error");
		}
		
		@Override
		public Map<String, String> getProperties() { return null; }

		@Override
		public String getValue(String propertyName) { return null; }

		@Override
		public String getName() { return null; }
	}
	
	public static class InstantiationErrorSource implements ConfigSource {
		
		static {
			Util.B(null, x -> { throw new RuntimeException("Error"); });
		}
		
		@Override
		public Map<String, String> getProperties() { return null; }

		@Override
		public String getValue(String propertyName) { return null; }

		@Override
		public String getName() { return null; }
	}
	
	public static class LongConvereter implements Converter<Long> {

		@Override
		public Long convert(String value) {
			return Long.valueOf(Util.crc(value.getBytes(Util.UTF8_CHARSET)));
		}
		
	}
	
	public static interface MyConvereter<T,K> extends Converter<T> {}
	
	public static class LongConverterWithTooManyParameters implements MyConvereter<Long, Long> {

		@Override
		public Long convert(String value) {
			return null;
		}
		
	}
	
	public static class ConverterWithBadConstructor implements Converter<Long> { 

		public ConverterWithBadConstructor(String param1) {}
		
		@Override
		public Long convert(String value) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	public static class ConverterWithErrorConstructor implements Converter<Long> { 

		public ConverterWithErrorConstructor() { throw new RuntimeException("Error"); }
		
		@Override
		public Long convert(String value) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	private byte[] getJarBytes() throws Throwable {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		Util.createJar(byteOut, JemoConfigBuilder.class, MyCustomerSource.class, MyUnacceptableSource.class, 
				ErrorThrowingSource.class, InstantiationErrorSource.class, LongConvereter.class,
				LongConverterWithTooManyParameters.class, ConverterWithBadConstructor.class, ConverterWithErrorConstructor.class);
		return byteOut.toByteArray();
	}
	
	private byte[] getEmptyJarBytes() throws Throwable {
		ByteArrayOutputStream byteOut = new ByteArrayOutputStream();
		Util.createJar(byteOut, JemoConfigBuilder.class);
		return byteOut.toByteArray();
	}
	
	private JemoClassLoader getTestClassLoader() throws Throwable {
		JemoClassLoader loader = new JemoClassLoader(UUID.randomUUID().toString(), getJarBytes());
		loader.setApplicationId(92001);
		return loader;
	}
	
	private JemoClassLoader getEmptyTestClassLoader() throws Throwable {
		JemoClassLoader loader = new JemoClassLoader(UUID.randomUUID().toString(), getEmptyJarBytes());
		loader.setApplicationId(92001);
		return loader;
	}
	
	@Test
	public void testAddDefaultSources() throws Throwable {
		byte[] jar = getJarBytes();
		JemoConfigBuilder builder = new JemoConfigBuilder(getTestClassLoader());
		builder.addDefaultSources();
		Config config = builder.build();
		assertEquals(2,StreamSupport.stream(config.getConfigSources().spliterator(),false)
			.filter(src -> src instanceof MicroProfileConfigSource || src instanceof JemoConfigSource)
			.count());
		assertTrue(StreamSupport.stream(config.getConfigSources().spliterator(),false).anyMatch(src -> src instanceof MicroProfileConfigSource));
		assertTrue(StreamSupport.stream(config.getConfigSources().spliterator(),false).anyMatch(src -> src instanceof JemoConfigSource));
	}
	
	@Test
	public void testAddDiscoveredSources() throws Throwable {
		JemoConfigBuilder builder = new JemoConfigBuilder(getTestClassLoader());
		builder.addDiscoveredSources();
		Config config = builder.build();
		assertTrue(config instanceof JemoConfig);
		assertTrue(config.getOptionalValue("custom", String.class).isPresent());
		assertEquals("value",config.getValue("custom", String.class));
	}
	
	@Test
	public void testAddDiscoveredConverters() throws Throwable {
		JemoConfigBuilder builder = new JemoConfigBuilder(getTestClassLoader());
		builder.addDiscoveredSources();
		builder.addDiscoveredConverters();
		Config config = builder.build();
		assertTrue(config instanceof JemoConfig);
		assertTrue(config.getOptionalValue("custom", Long.class).isPresent());
		assertEquals(Long.valueOf(Util.crc("value".getBytes(Util.UTF8_CHARSET))),config.getValue("custom", Long.class));
	}
	
	@Test
	public void testForClassLoader() throws Throwable {
		JemoConfigBuilder builder = new JemoConfigBuilder(getTestClassLoader());
		builder.addDiscoveredSources();
		builder.addDiscoveredConverters();
		//now we are going to set a non Jemo class loader derivative and we expect nothing to change.
		builder.forClassLoader(new ClassLoader() {});
		Config config = builder.build();
		assertTrue(config instanceof JemoConfig);
		assertTrue(config.getOptionalValue("custom", Long.class).isPresent());
		assertEquals(Long.valueOf(Util.crc("value".getBytes(Util.UTF8_CHARSET))),config.getValue("custom", Long.class));
		//now we are going to set a jemo class loader with nothing in it.
		builder.forClassLoader(getEmptyTestClassLoader());
		config = builder.build();
		assertTrue(config instanceof JemoConfig);
		assertFalse(config.getOptionalValue("custom", Long.class).isPresent());
	}
	
	@Test
	public void testWithSources() throws Throwable {
		JemoClassLoader classLoader = getTestClassLoader();
		JemoConfigBuilder builder = new JemoConfigBuilder(classLoader);
		builder.withSources(
				new MicroProfileConfigSource(classLoader), 
				new JemoConfigSource(Util.MAP(new AbstractMap.SimpleEntry<>("custom2","value2"))), 
				new MyCustomerSource());
		Config config = builder.build();
		assertTrue(config instanceof JemoConfig);
		assertTrue(config.getOptionalValue("custom", String.class).isPresent());
		assertTrue(config.getOptionalValue("custom2", String.class).isPresent());
		assertEquals("value", config.getValue("custom", String.class));
		assertEquals("value2", config.getValue("custom2", String.class));
	}
	
	@Test
	public void testWithConverters() throws Throwable {
		JemoClassLoader classLoader = getTestClassLoader();
		JemoConfigBuilder builder = new JemoConfigBuilder(classLoader);
		builder.withSources(
				new MicroProfileConfigSource(classLoader), 
				new JemoConfigSource(Util.MAP(new AbstractMap.SimpleEntry<>("custom2","value2"))), 
				new MyCustomerSource());
		builder.withConverters(new LongConvereter());
		Config config = builder.build();
		assertTrue(config instanceof JemoConfig);
		assertTrue(config.getOptionalValue("custom", Long.class).isPresent());
		assertEquals(Long.valueOf(Util.crc("value".getBytes(Util.UTF8_CHARSET))),config.getValue("custom", Long.class));
	}
	
	@Test
	public void testWithConverter() throws Throwable {
		JemoClassLoader classLoader = getTestClassLoader();
		JemoConfigBuilder builder = new JemoConfigBuilder(classLoader);
		builder.withSources(
				new MicroProfileConfigSource(classLoader), 
				new JemoConfigSource(Util.MAP(new AbstractMap.SimpleEntry<>("custom2","value2"))), 
				new MyCustomerSource());
		builder.withConverter(Long.class, 1, (v) -> Util.crc(v.getBytes(Util.UTF8_CHARSET)));
		Config config = builder.build();
		assertTrue(config instanceof JemoConfig);
		assertTrue(config.getOptionalValue("custom", Long.class).isPresent());
		assertEquals(Long.valueOf(Util.crc("value".getBytes(Util.UTF8_CHARSET))),config.getValue("custom", Long.class));
	}
}
