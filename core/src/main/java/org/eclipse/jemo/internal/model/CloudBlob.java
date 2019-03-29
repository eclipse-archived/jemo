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
package org.eclipse.jemo.internal.model;

import org.eclipse.jemo.Jemo;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * this class will define the data that a cloud large binary object storage system should provide.
 * 
 * @author Christopher Stura "christopher.stura@cloudreach.com"
 */
public class CloudBlob {
	private String key = null;
	private long createdDate = 0;
	private InputStream dataStream = null;
	private long length = 0;
	private byte[] rawData = null;
	
	public CloudBlob() {}
	
	public CloudBlob(String key,long createdDate,long length,InputStream dataStream) {
		this.key = key;
		this.createdDate = createdDate;
		this.dataStream = dataStream;
		this.length = length;
	}

	public long getCreatedDate() {
		return createdDate;
	}

	public String getKey() {
		return key;
	}

	public long getLength() {
		return length;
	}

	@JsonIgnore
	public InputStream getDataStream() {
		if(rawData == null) {
			return dataStream;
		} else {
			return new ByteArrayInputStream(rawData);
		}
	}
	
	@JsonProperty(value = "data")
	public byte[] getData() throws IOException {
		if(rawData == null) {
			ByteArrayOutputStream byteOut = new ByteArrayOutputStream((int)length);
			Jemo.stream(byteOut, getDataStream());
			rawData = byteOut.toByteArray();
		}
		
		return rawData;
	}

	public void setKey(String key) {
		this.key = key;
	}

	public void setCreatedDate(long createdDate) {
		this.createdDate = createdDate;
	}

	public void setLength(long length) {
		this.length = length;
	}

	@JsonProperty(value = "data")
	public void setRawData(byte[] rawData) {
		this.rawData = rawData;
	}
	
	
}
