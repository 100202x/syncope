/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.syncope.common.lib.policy;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.PathParam;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlElementWrapper;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;
import org.apache.syncope.common.lib.AbstractBaseBean;
import org.apache.syncope.common.lib.to.EntityTO;

@XmlRootElement(name = "policy")
@XmlType
@XmlSeeAlso({ AccountPolicyTO.class, PasswordPolicyTO.class, PullPolicyTO.class })
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "@class")
@JsonPropertyOrder(value = { "@class", "key", "description" })
@ApiModel(subTypes = { AccountPolicyTO.class, PasswordPolicyTO.class, PullPolicyTO.class }, discriminator = "@class")
public abstract class PolicyTO extends AbstractBaseBean implements EntityTO {

    private static final long serialVersionUID = -2903888572649721035L;

    @XmlTransient
    @JsonProperty("@class")
    private String discriminator;

    private String key;

    private String description;

    private final List<String> usedByResources = new ArrayList<>();

    private final List<String> usedByRealms = new ArrayList<>();

    @ApiModelProperty(name = "@class", required = true, readOnly = false)
    public abstract String getDiscriminator();

    public void setDiscriminator(final String discriminator) {
        // do nothing
    }

    @ApiModelProperty(readOnly = true)
    @Override
    public String getKey() {
        return key;
    }

    @PathParam("key")
    @Override
    public void setKey(final String key) {
        this.key = key;
    }

    @JsonProperty(required = true)
    @XmlElement(required = true)
    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    @ApiModelProperty(readOnly = true)
    @XmlElementWrapper(name = "usedByResources")
    @XmlElement(name = "resource")
    @JsonProperty("usedByResources")
    public List<String> getUsedByResources() {
        return usedByResources;
    }

    @ApiModelProperty(readOnly = true)
    @XmlElementWrapper(name = "usedByRealms")
    @XmlElement(name = "group")
    @JsonProperty("usedByRealms")
    public List<String> getUsedByRealms() {
        return usedByRealms;
    }

}
