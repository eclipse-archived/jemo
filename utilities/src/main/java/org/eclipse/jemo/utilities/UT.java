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
package org.eclipse.jemo.utilities;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.conn.ssl.NoopHostnameVerifier;
import org.apache.http.conn.ssl.TrustStrategy;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.ssl.SSLContextBuilder;

/**
 *
 * @author christopher stura
 */
public class UT {
	public static HttpClient HTTP_CLIENT() throws NoSuchAlgorithmException,KeyStoreException,KeyManagementException {
		return HttpClients.custom().setSSLHostnameVerifier(NoopHostnameVerifier.INSTANCE)
															.setSSLContext(new SSLContextBuilder().loadTrustMaterial((TrustStrategy) (X509Certificate[] xcs, String string) -> true).build()).build();
	}
	
	public static HttpResponse GET(String url, String username, String password) throws NoSuchAlgorithmException,KeyStoreException,KeyManagementException,UnsupportedEncodingException,IOException {
		HttpClient httpClient = HTTP_CLIENT();
		HttpGet getRequest = new HttpGet(url);
		getRequest.setHeader("Authorization", "Basic "+java.util.Base64.getEncoder().encodeToString((username+":"+password).getBytes("UTF-8")));
		return httpClient.execute(getRequest);
	}
	
	public static <T extends Object> T fromJSON(Class<T> cls,String jsonData) throws IOException {
		if(jsonData != null && !jsonData.isEmpty()) {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
			TypeFactory tf = TypeFactory.defaultInstance().withClassLoader(cls.getClassLoader());
			mapper.setTypeFactory(tf);
			
			return mapper.readValue(jsonData, cls);
		}
		
		return null;
	}
	
	public static <T extends Object> List<T> fromJSONArray(Class<T> cls,String jsonData) throws IOException {
		if(jsonData == null) {
			return null;
		}
		ObjectMapper mapper = new ObjectMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
		TypeFactory tf = TypeFactory.defaultInstance().withClassLoader(cls.getClassLoader());
		mapper.setTypeFactory(tf);
		
		return mapper.readValue(jsonData, mapper.getTypeFactory().constructCollectionType(List.class, cls));
	}
	
	public static String toJSON(Object obj) throws RuntimeException {
		try {
			ObjectMapper mapper = new ObjectMapper();
			mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
			mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

			return mapper.writeValueAsString(obj);
		}catch(JsonProcessingException jsonEx) {
			throw new RuntimeException(jsonEx);
		}
	}
	
	public static String toString(InputStream in) throws IOException {
		StringBuilder out = new StringBuilder();
		byte[] buf = new byte[8192];
		int rb = 0;
		while((rb = in.read(buf)) != -1) {
			out.append(new String(buf,0,rb,"UTF-8"));
		}
		in.close();
		return out.toString();
	}
	
	public static String toString(Reader in) throws IOException {
		StringBuilder out = new StringBuilder();
		char[] cBuf = new char[8192];
		int rb = 0;
		while((rb = in.read(cBuf)) != -1) {
			out.append(new String(cBuf,0,rb));
		}
		in.close();
		return out.toString();
	}
	
	public static void stream(OutputStream out,InputStream in) throws IOException {
		byte[] buf = new byte[8192];
		int rb = 0;
		while((rb = in.read(buf)) != -1) {
			out.write(buf,0,rb);
			out.flush();
		}
		in.close();
	}
	
	public static String S(String str) {
		return (str == null ? "" : str);
	}
	
	public static <T extends Object> Map<T,T> MAP(Collection<T> collection) {
		Map<T,T> result = new LinkedHashMap<>(collection.size());
		collection.forEach(k -> result.put(k,k));
		return result;
	}
	
	public static String getContentTypeFromFileName(String filename) {
		if(filename.toLowerCase().endsWith(".pdf")) {
			return "application/pdf";
		} else if(filename.toLowerCase().endsWith(".doc")) {
			return "application/msword";
		} else if(filename.toLowerCase().endsWith(".docx")) {
			return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
		} else if(filename.toLowerCase().endsWith(".xls")) {
			return "application/vnd.ms-excel";
		} else if(filename.toLowerCase().endsWith(".xlsx")) {
			return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
		} else if(filename.toLowerCase().endsWith(".tif") || filename.toLowerCase().endsWith(".tiff")) {
			return "image/tiff";
		} else if(filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
			return "image/jpeg";
		} else {
			return "application/octet-stream";
		}
	}
	
	public static <T extends Object> T COALESCE(T obj, T alternative) {
		return obj != null ? obj : alternative;
	}
	
	public static final SimpleDateFormat DATE_SIMPLE = new SimpleDateFormat("dd-MM-yyyy");
	public static final SimpleDateFormat DATE_SIMPLE_VAR1 = new SimpleDateFormat("dd/MM/yyyy");
	public static final SimpleDateFormat DATE_SIMPLE_VAR2 = new SimpleDateFormat("ddMMyyyy");
	public static final SimpleDateFormat TIME_SIMPLE = new SimpleDateFormat("HH:mm");
	public static final SimpleDateFormat TIME_WITH_SECONDS = new SimpleDateFormat("HH:mm:ss");
	public static final SimpleDateFormat DATETIME_SIMPLE = new SimpleDateFormat("dd-MM-yyyy HH:mm");
	public static final SimpleDateFormat DATETIME_SIMPLE_VAR1 = new SimpleDateFormat("dd/MM/yyyy HH:mm");
	public static final SimpleDateFormat DATETIME_SIMPLE_VAR2 = new SimpleDateFormat("ddMMyyyy HH:mm");
	
	public static java.util.Date DATE(String dateStr) throws ParseException {
		SimpleDateFormat cdf = Arrays.asList(DATE_SIMPLE,DATE_SIMPLE_VAR1,DATE_SIMPLE_VAR2,TIME_SIMPLE,TIME_WITH_SECONDS,DATETIME_SIMPLE,DATETIME_SIMPLE_VAR1,DATETIME_SIMPLE_VAR2)
						.stream().filter((df) -> {
							try { df.parse(dateStr); return true; } catch(ParseException ex) { return false; }
						}).findAny().orElse(null);
		
		return (cdf != null ? cdf.parse(dateStr) : null);
	}
	
	public static String md5(String origStr) throws NoSuchAlgorithmException,UnsupportedEncodingException {
		MessageDigest md5 = MessageDigest.getInstance("MD5");
		md5.update(origStr.getBytes("UTF-8"));
		return String.format("%032x", new BigInteger(1, md5.digest()));
	}
	
	public static final <R extends Object, T extends Object> R executeFailsafe(Function<T,R> func,T value) {
		try { return func.apply(value); } catch(Throwable ex) { return null; }
	}
}
