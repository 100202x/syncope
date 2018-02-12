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
package org.apache.syncope.common.lib.to;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import javax.xml.bind.annotation.XmlElement;
import javax.xml.bind.annotation.XmlRootElement;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlTransient;
import javax.xml.bind.annotation.XmlType;

@XmlRootElement(name = "schedTask")
@XmlType
@XmlSeeAlso({ ProvisioningTaskTO.class })
@ApiModel(parent = TaskTO.class, subTypes = { ProvisioningTaskTO.class }, discriminator = "@class")
public class SchedTaskTO extends TaskTO {

    private static final long serialVersionUID = -5722284116974636425L;

    private Date startAt;

    private String cronExpression;

    private String jobDelegateClassName;

    private String name;

    private String description;

    private Date lastExec;

    private Date nextExec;

    private boolean active = true;

    @XmlTransient
    @JsonProperty("@class")
    @ApiModelProperty(name = "@class", required = true, example = "org.apache.syncope.common.lib.to.SchedTaskTO")
    @Override
    public String getDiscriminator() {
        return getClass().getName();
    }

    public Date getStartAt() {
        return startAt == null ? null : new Date(startAt.getTime());
    }

    public void setStartAt(final Date start) {
        this.startAt = start == null ? null : new Date(start.getTime());
    }

    public String getCronExpression() {
        return cronExpression;
    }

    public void setCronExpression(final String cronExpression) {
        this.cronExpression = cronExpression;
    }

    public String getJobDelegateClassName() {
        return jobDelegateClassName;
    }

    public void setJobDelegateClassName(final String jobDelegateClassName) {
        this.jobDelegateClassName = jobDelegateClassName;
    }

    @ApiModelProperty(readOnly = true)
    public Date getLastExec() {
        return lastExec == null ? null : new Date(lastExec.getTime());
    }

    public void setLastExec(final Date lastExec) {
        this.lastExec = lastExec == null ? null : new Date(lastExec.getTime());
    }

    @ApiModelProperty(readOnly = true)
    public Date getNextExec() {
        return nextExec == null ? null : new Date(nextExec.getTime());
    }

    public void setNextExec(final Date nextExec) {
        this.nextExec = nextExec == null ? null : new Date(nextExec.getTime());
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(final String description) {
        this.description = description;
    }

    public String getName() {
        return name;
    }

    @JsonProperty(required = true)
    @XmlElement(required = true)
    public void setName(final String name) {
        this.name = name;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(final boolean active) {
        this.active = active;
    }
}
