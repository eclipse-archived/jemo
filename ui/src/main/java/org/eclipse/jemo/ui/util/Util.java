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
package org.eclipse.jemo.ui.util;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.zip.CRC32;

/**
 * @author christopherstura
 */
public class Util {

    private static final Random RND = new Random(System.currentTimeMillis());

    public static int getRandomInt() {
        return RND.nextInt(100);
    }

    public static long CRC32(String str) throws UnsupportedEncodingException {
        CRC32 crc = new CRC32();
        crc.update(str.getBytes("UTF-8"));
        return crc.getValue();
    }

    public static <T extends Object> T fromJSON(Class<T> cls, String jsonData) throws IOException {
        if (jsonData != null && !jsonData.isEmpty()) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

            return mapper.readValue(jsonData, cls);
        }

        return null;
    }

    public static <T extends Object> List<T> fromJSONArray(Class<T> cls, String jsonData) throws IOException {
        if (jsonData == null) {
            return null;
        }
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return mapper.readValue(jsonData, mapper.getTypeFactory().constructCollectionType(List.class, cls));
    }

    public static String toJSON(Object obj) throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);

        return mapper.writeValueAsString(obj);
    }

    public static String toString(InputStream in) throws IOException {
        StringBuilder out = new StringBuilder();
        byte[] buf = new byte[8192];
        int rb = 0;
        while ((rb = in.read(buf)) != -1) {
            out.append(new String(buf, 0, rb, "UTF-8"));
        }
        in.close();
        return out.toString();
    }

    public static String toString(Reader in) throws IOException {
        StringBuilder out = new StringBuilder();
        char[] cBuf = new char[8192];
        int rb = 0;
        while ((rb = in.read(cBuf)) != -1) {
            out.append(new String(cBuf, 0, rb));
        }
        in.close();
        return out.toString();
    }

    public static void stream(OutputStream out, InputStream in) throws IOException {
        byte[] buf = new byte[8192];
        int rb = 0;
        while ((rb = in.read(buf)) != -1) {
            out.write(buf, 0, rb);
            out.flush();
        }
        in.close();
    }

    public static String S(String str) {
        return (str == null ? "" : str.trim());
    }

    public static <T extends Object> Map<T, T> MAP(Collection<T> collection) {
        Map<T, T> result = new LinkedHashMap<>(collection.size());
        collection.forEach(k -> result.put(k, k));
        return result;
    }

    public static String getContentTypeFromFileName(String filename) {
        if (filename.toLowerCase().endsWith(".pdf")) {
            return "application/pdf";
        } else if (filename.toLowerCase().endsWith(".doc")) {
            return "application/msword";
        } else if (filename.toLowerCase().endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        } else if (filename.toLowerCase().endsWith(".xls")) {
            return "application/vnd.ms-excel";
        } else if (filename.toLowerCase().endsWith(".xlsx")) {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        } else if (filename.toLowerCase().endsWith(".tif") || filename.toLowerCase().endsWith(".tiff")) {
            return "image/tiff";
        } else if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
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
    public static final SimpleDateFormat DATE_SIMPLE_VAR3 = new SimpleDateFormat("yyyy-MM-dd");
    public static final SimpleDateFormat TIME_SIMPLE = new SimpleDateFormat("HH:mm");
    public static final SimpleDateFormat TIME_WITH_SECONDS = new SimpleDateFormat("HH:mm:ss");
    public static final SimpleDateFormat DATETIME_SIMPLE = new SimpleDateFormat("dd-MM-yyyy HH:mm");
    public static final SimpleDateFormat DATETIME_SIMPLE_VAR1 = new SimpleDateFormat("dd/MM/yyyy HH:mm");
    public static final SimpleDateFormat DATETIME_SIMPLE_VAR2 = new SimpleDateFormat("ddMMyyyy HH:mm");
    public static final SimpleDateFormat DATETIME_SIMPLE_VAR3 = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public static final SimpleDateFormat DATE_SQL = DATE_SIMPLE_VAR3;
    public static final SimpleDateFormat DATETIME_SQL = DATETIME_SIMPLE_VAR3;

    public static java.util.Date DATE(String dateStr) throws ParseException {
        SimpleDateFormat cdf = Arrays.asList(DATE_SIMPLE, DATE_SIMPLE_VAR1, DATE_SIMPLE_VAR2, DATE_SIMPLE_VAR3, TIME_SIMPLE, TIME_WITH_SECONDS, DATETIME_SIMPLE, DATETIME_SIMPLE_VAR1, DATETIME_SIMPLE_VAR2, DATETIME_SIMPLE_VAR3)
                .stream().filter((df) -> {
                    try {
                        java.util.Date parsedDate = df.parse(dateStr);
                        String formattedDate = df.format(parsedDate);
                        return (formattedDate.equalsIgnoreCase(dateStr));
                    } catch (ParseException ex) {
                        return false;
                    }
                }).findAny().orElse(null);

        return (cdf != null ? cdf.parse(dateStr) : null);
    }

    public static byte[] base64(String base64encodedString) {
        return new ObjectMapper().convertValue(base64encodedString, byte[].class);
    }

    public static String base64(byte[] bytesToEncode) {
        return new ObjectMapper().convertValue(bytesToEncode, String.class);
    }

    /**
     * this method will weed out any non ascii characters and all control characters. This includes a range of characters between decimal 20 and decimal 128
     *
     * @param str an input string with any java encoded character string.
     * @return a ascii normalised string.
     */
    public static String ASCII(String str) {
        return str.chars()
                .filter(c -> c >= 20 && c <= 128)
                .collect(() -> new StringBuilder(),
                        (r, c) -> r.append((char) c), (r1, r2) -> r1.append(r2)).toString();
    }

    public static String JSON_FROM_STRING(String jsonString) throws JsonProcessingException, IOException {
        ObjectMapper mapper = new ObjectMapper();
        return mapper.readValue(jsonString, String.class);
    }

    public static String PRETTY_PRINT_JSON(String jsonString) throws JsonProcessingException, IOException {
        if (S(jsonString).trim().startsWith("[") || S(jsonString).trim().startsWith("{")) {
            ObjectMapper mapper = new ObjectMapper();
            Object jsonObj = mapper.readValue(jsonString, Object.class);
            return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonObj);
        }

        return null;
    }

    /**
     * this method will return a string which is at maximum maxLength long
     *
     * @param str       the string to limit in size
     * @param maxLength the maximum length to limit the string to
     * @return a string which is has a maximum length of maxLength.
     */
    public static String truncate(String str, int maxLength) {
        if (str == null || str.length() <= maxLength) {
            return str;
        } else {
            return str.substring(0, maxLength);
        }
    }

    public static <T, P> T F(P param, ManagedFunction<T, P> function) {
        try {
            return function.run(param);
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
}
