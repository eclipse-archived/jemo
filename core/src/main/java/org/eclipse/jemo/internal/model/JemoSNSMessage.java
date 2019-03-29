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

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 *
 * @author christopherstura
 */
public class JemoSNSMessage {
	private String type = null;
	private String messageId = null;
	private String topicArn = null;
	private String message = null;

	@JsonProperty(value = "Type")
	public String getType() {
		return type;
	}

	@JsonProperty(value = "Type")
	public void setType(String type) {
		this.type = type;
	}

	@JsonProperty(value = "MessageId")
	public String getMessageId() {
		return messageId;
	}

	@JsonProperty(value = "MessageId")
	public void setMessageId(String messageId) {
		this.messageId = messageId;
	}

	@JsonProperty(value = "TopicArn")
	public String getTopicArn() {
		return topicArn;
	}

	@JsonProperty(value = "TopicArn")
	public void setTopicArn(String topicArn) {
		this.topicArn = topicArn;
	}

	@JsonProperty(value = "Message")
	public String getMessage() {
		return message;
	}

	@JsonProperty(value = "Message")
	public void setMessage(String message) {
		this.message = message;
	}
	
	
}
