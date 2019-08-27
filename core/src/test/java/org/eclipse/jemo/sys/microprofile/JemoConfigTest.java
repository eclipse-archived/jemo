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

import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.UUID;
import java.util.stream.StreamSupport;

import javax.annotation.Priority;

import static java.util.AbstractMap.SimpleEntry;

import org.eclipse.jemo.sys.JemoClassLoader;
import org.eclipse.jemo.sys.internal.JarEntry;
import org.eclipse.jemo.sys.internal.ManagedConsumer;
import org.eclipse.jemo.sys.internal.Util;
import org.eclipse.microprofile.config.spi.ConfigSource;
import org.eclipse.microprofile.config.spi.Converter;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Map;

/**
 * The purpose of this test file is to ensure that the class org.eclipse.jemo.sys.microprofile.JemoConfig
 * obtains 100% code coverage from a unit test standpoint.
 * 
 * Because this class can only be used within an Active Jemo module we will need to instantiate the server to make sure these
 * tests run correctly.
 * 
 * @author Christopher Stura "cstura@gmail.com"
 */
public class JemoConfigTest {
	
	private <C> void assertException(ManagedConsumer<C> consumer, Class<? extends Throwable> errorType) throws AssertionError {
		try {
			consumer.accept(null);
			throw new AssertionError("An error of type "+errorType.getName()+" was never thrown");
		}catch(Throwable ex) {
			if(!errorType.isAssignableFrom(ex.getClass())) {
				throw new AssertionError("The error thrown is not of type "+errorType.getName()+" but is instead "+ex.getClass().getName(), ex);
			} else if(ex instanceof AssertionError) {
				throw AssertionError.class.cast(ex);
			}
		}
	}
	
	@Test
	public void testGetValue() {
		JemoConfig config = new JemoConfig();
		assertException((x) -> config.getValue("key", String.class),NoSuchElementException.class);
		config.setConfigSource(new JemoConfigSource(Util.MAP(new SimpleEntry<>("key","value"))));
		assertEquals("value",config.getValue("key", String.class));
	}
	
	@Priority(value = 100)
	public static class IntConverter100 implements Converter<Integer> {

		@Override
		public Integer convert(String value) {
			// TODO Auto-generated method stub
			return 10;
		}
		
	}
	
	@Priority(value = 50)
	public static class IntConverter50 implements Converter<Integer> {

		@Override
		public Integer convert(String value) {
			// TODO Auto-generated method stub
			return 5;
		}
		
	}
	
	public static class IntConverterDefaultPriority implements Converter<Integer> {
		@Override
		public Integer convert(String value) {
			// TODO Auto-generated method stub
			return 7;
		}
	}
	
	public static interface MyConverter<T, K> extends Converter<T> {}
	
	public static class InvalidConverterWithTooManyParameters implements MyConverter<String, String> {

		@Override
		public String convert(String value) {
			// TODO Auto-generated method stub
			return null;
		}
		
	}
	
	@Test
	public void testGetOptionalValue() {
		JemoConfig config = new JemoConfig();
		assertFalse(config.getOptionalValue("key", String.class).isPresent());
		config.setConfigSource(new JemoConfigSource(Util.MAP(new SimpleEntry<>("key","value"), new SimpleEntry<>("json","{ \"v\" : \"value\" }"))));
		assertTrue(config.getOptionalValue("key", String.class).isPresent());
		assertException((x) -> config.getOptionalValue("key", Long.class), IllegalArgumentException.class);
		Map<String,String> json = config.getValue("json", Map.class);
		assertEquals("value", json.get("v"));
		config.addDataConverter(new JemoConfig.TypedConverter(Long.class, 100, (v) -> Long.valueOf(10)));
		config.addDataConverter(new JemoConfig.TypedConverter(Long.class, 50, (v) -> Long.valueOf(5)));
		assertEquals(Long.valueOf(5), config.getValue("key", Long.class));
		config.addDataConverter(new IntConverter100());
		config.addDataConverter(new IntConverter50());
		config.addDataConverter(new InvalidConverterWithTooManyParameters());
		config.addDataConverter(new IntConverterDefaultPriority());
		assertEquals(Integer.valueOf(5), config.getValue("key", Integer.class));
	}
	
	@Test
	public void testGetPropertyNames() {
		JemoConfig config = new JemoConfig(Util.MAP(new SimpleEntry<>("key","value"), new SimpleEntry<>("json","{ \"v\" : \"value\" }")), null);
		assertArrayEquals(new String[] {"json","key"}, StreamSupport.stream(config.getPropertyNames().spliterator(),false).sorted().toArray(String[]::new));
	}
	
	@Test
	public void testSetMicroProfileSource() throws Throwable {
		JemoConfig config = new JemoConfig();
		ByteArrayOutputStream jarBytes = new ByteArrayOutputStream();
		final Properties appConfig = new Properties();
		appConfig.setProperty("test", "value");
		Util.createJar(jarBytes, JarEntry.fromProperties(MicroProfileConfigSource.MICROPROFILE_CONFIG, appConfig));
		config.setMicroProfileSource(new MicroProfileConfigSource(new JemoClassLoader(UUID.randomUUID().toString(), jarBytes.toByteArray())));
		assertEquals("value", config.getValue("test", String.class));
		config.setConfigSource(new JemoConfigSource(Util.MAP(new SimpleEntry<>("key","value"))));
		assertEquals("value", config.getValue("key", String.class));
		config.addConfigSource(new ConfigSource() {
			
			@Override
			public String getValue(String propertyName) {
				// TODO Auto-generated method stub
				return getProperties().get(propertyName);
			}
			
			@Override
			public Map<String, String> getProperties() {
				// TODO Auto-generated method stub
				return Util.MAP(new SimpleEntry<>("key2","value2"));
			}
			
			@Override
			public String getName() {
				// TODO Auto-generated method stub
				return "Inline";
			}
		});
		assertEquals("value2", config.getValue("key2", String.class));
	}
}
