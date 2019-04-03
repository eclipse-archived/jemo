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
package org.eclipse.jemo.runtime.azure;

import javax.crypto.BadPaddingException;
import javax.crypto.Cipher;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.KeySpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Created by alirezafallahi on 17/02/16.
 * See Page 38 : http://www.sagepay.co.uk/file/25041/download-document/FORM_Integration_and_Protocol_Guidelines_270815.pdf?token=cPVKBIYVFlUvhtarLjtjcgzmuYzWPjIbuZQocUKFrXU
 */

public class AESCrypt {

    public AESCrypt() {
        super();
    }
		
		public static byte[] getKeyFromPassword(String password) throws InvalidKeySpecException,NoSuchAlgorithmException,UnsupportedEncodingException {
			PBEKeySpec spec = new PBEKeySpec(password.toCharArray(),new byte[] {0,1,1,3,4,9,1,2,0,1,1,3,4,9,1,2},65536,128); // AES-256
			SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
			return f.generateSecret(spec).getEncoded();
		}

    public static byte[] AESEncrypt(String sEncrypt, String charset, String pwd) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, UnsupportedEncodingException {
        return AES(1, sEncrypt.getBytes(charset), pwd.getBytes(charset));
    }
		
		public static byte[] AESEncrypt(String sEncrypt, String charset, byte[] key) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, IllegalBlockSizeException, BadPaddingException, InvalidAlgorithmParameterException, UnsupportedEncodingException {
        return AES(1, sEncrypt.getBytes(charset), key);
    }

    public static byte[] AESDecrypt(byte[] data, String pwd) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,UnsupportedEncodingException {
        return AES(2, data, pwd.getBytes("UTF-8"));
    }
		
		public static byte[] AESDecrypt(byte[] data, byte[] key) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException,UnsupportedEncodingException {
        return AES(2, data, key);
    }

    private static byte[] AES(int opmode, byte[] data, byte[] pwd) throws NoSuchAlgorithmException, NoSuchPaddingException, InvalidKeyException, InvalidAlgorithmParameterException, IllegalBlockSizeException, BadPaddingException {
        byte[] key_str = pwd;
        SecretKeySpec sks = new SecretKeySpec(key_str, "AES");
        Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
        cipher.init(opmode, sks, new IvParameterSpec(key_str));
        return cipher.doFinal(data);
    }
}