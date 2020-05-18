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

import io.swagger.annotations.ApiModelProperty;
import java.util.Date;
import javax.xml.bind.annotation.XmlSeeAlso;
import javax.xml.bind.annotation.XmlType;
import org.apache.syncope.common.lib.AbstractBaseBean;

@XmlType
@XmlSeeAlso({ ExecTO.class, ReportTO.class, TaskTO.class })
public class AbstractStartEndBean extends AbstractBaseBean {

    private static final long serialVersionUID = 2399577415544539917L;

    private Date start;

    private Date end;

    @ApiModelProperty(readOnly = true)
    public Date getStart() {
        return start == null
                ? null
                : new Date(start.getTime());
    }

    public void setStart(final Date start) {
        this.start = start == null
                ? null
                : new Date(start.getTime());
    }

    @ApiModelProperty(readOnly = true)
    public Date getEnd() {
        return end == null
                ? null
                : new Date(end.getTime());
    }

    public void setEnd(final Date end) {
        this.end = end == null
                ? null
                : new Date(end.getTime());
    }
}
