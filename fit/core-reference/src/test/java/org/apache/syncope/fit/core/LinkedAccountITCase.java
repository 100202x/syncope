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
package org.apache.syncope.fit.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.ldap.LdapContext;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.syncope.common.lib.patch.LinkedAccountPatch;
import org.apache.syncope.common.lib.patch.UserPatch;
import org.apache.syncope.common.lib.to.LinkedAccountTO;
import org.apache.syncope.common.lib.to.PagedResult;
import org.apache.syncope.common.lib.to.PropagationTaskTO;
import org.apache.syncope.common.lib.to.UserTO;
import org.apache.syncope.common.lib.types.AnyTypeKind;
import org.apache.syncope.common.lib.types.ExecStatus;
import org.apache.syncope.common.lib.types.PatchOperation;
import org.apache.syncope.common.lib.types.ResourceOperation;
import org.apache.syncope.common.lib.types.TaskType;
import org.apache.syncope.common.rest.api.beans.TaskQuery;
import org.apache.syncope.fit.AbstractITCase;
import org.junit.jupiter.api.Test;

public class LinkedAccountITCase extends AbstractITCase {

    @Test
    public void createWithLinkedAccountThenRemove() throws NamingException {
        // 1. create user with linked account
        UserTO user = UserITCase.getSampleTO(
                "linkedAccount" + RandomStringUtils.randomNumeric(5) + "@syncope.apache.org");
        String connObjectName = "uid=" + user.getUsername() + ",ou=People,o=isp";
        String privilege = applicationService.read("mightyApp").getPrivileges().get(0).getKey();

        LinkedAccountTO account = new LinkedAccountTO.Builder().
                connObjectName(connObjectName).
                resource(RESOURCE_NAME_LDAP).
                build();
        account.getPlainAttrs().add(attrTO("surname", "LINKED_SURNAME"));
        account.getPrivileges().add(privilege);
        user.getLinkedAccounts().add(account);

        user = createUser(user).getEntity();
        assertNotNull(user.getKey());
        assertEquals(privilege, user.getLinkedAccounts().get(0).getPrivileges().iterator().next());

        // 2. verify that propagation task was generated and that account is found on resource
        PagedResult<PropagationTaskTO> tasks = taskService.search(
                new TaskQuery.Builder(TaskType.PROPAGATION).resource(RESOURCE_NAME_LDAP).
                        anyTypeKind(AnyTypeKind.USER).entityKey(user.getKey()).build());
        assertEquals(1, tasks.getTotalCount());
        assertEquals(connObjectName, tasks.getResult().get(0).getConnObjectKey());
        assertEquals(ResourceOperation.CREATE, tasks.getResult().get(0).getOperation());
        assertEquals(ExecStatus.SUCCESS.name(), tasks.getResult().get(0).getLatestExecStatus());

        LdapContext ldapObj = (LdapContext) getLdapRemoteObject(
                RESOURCE_LDAP_ADMIN_DN, RESOURCE_LDAP_ADMIN_PWD, connObjectName);
        assertNotNull(ldapObj);

        Attributes ldapAttrs = ldapObj.getAttributes("");
        assertEquals(
                user.getPlainAttr("email").get().getValues().get(0),
                ldapAttrs.get("mail").getAll().next().toString());
        assertEquals("LINKED_SURNAME", ldapAttrs.get("sn").getAll().next().toString());

        // 3. remove linked account from user
        UserPatch userPatch = new UserPatch();
        userPatch.setKey(user.getKey());
        userPatch.getLinkedAccounts().add(new LinkedAccountPatch.Builder().
                operation(PatchOperation.DELETE).
                linkedAccountTO(user.getLinkedAccounts().get(0)).build());

        user = updateUser(userPatch).getEntity();
        assertTrue(user.getLinkedAccounts().isEmpty());

        // 4. verify that propagation task was generated and that account is not any more on resource
        tasks = taskService.search(
                new TaskQuery.Builder(TaskType.PROPAGATION).resource(RESOURCE_NAME_LDAP).
                        anyTypeKind(AnyTypeKind.USER).entityKey(user.getKey()).build());
        assertEquals(2, tasks.getTotalCount());

        Optional<PropagationTaskTO> deletTask =
                tasks.getResult().stream().filter(task -> task.getOperation() == ResourceOperation.DELETE).findFirst();
        assertTrue(deletTask.isPresent());
        assertEquals(connObjectName, deletTask.get().getConnObjectKey());
        assertEquals(ExecStatus.SUCCESS.name(), deletTask.get().getLatestExecStatus());

        assertNull(getLdapRemoteObject(RESOURCE_LDAP_ADMIN_DN, RESOURCE_LDAP_ADMIN_PWD, connObjectName));
    }

    @Test
    public void createWithoutLinkedAccountThenAdd() throws NamingException {
        // 1. create user without linked account
        UserTO user = UserITCase.getSampleTO(
                "linkedAccount" + RandomStringUtils.randomNumeric(5) + "@syncope.apache.org");
        String connObjectName = "uid=" + user.getUsername() + ",ou=People,o=isp";

        user = createUser(user).getEntity();
        assertNotNull(user.getKey());
        assertTrue(user.getLinkedAccounts().isEmpty());

        PagedResult<PropagationTaskTO> tasks = taskService.search(
                new TaskQuery.Builder(TaskType.PROPAGATION).resource(RESOURCE_NAME_LDAP).
                        anyTypeKind(AnyTypeKind.USER).entityKey(user.getKey()).build());
        assertEquals(0, tasks.getTotalCount());

        assertNull(getLdapRemoteObject(RESOURCE_LDAP_ADMIN_DN, RESOURCE_LDAP_ADMIN_PWD, connObjectName));

        // 2. add linked account to user
        UserPatch userPatch = new UserPatch();
        userPatch.setKey(user.getKey());

        LinkedAccountTO account = new LinkedAccountTO.Builder().
                connObjectName(connObjectName).
                resource(RESOURCE_NAME_LDAP).
                build();
        account.getPlainAttrs().add(attrTO("surname", "LINKED_SURNAME"));
        account.setPassword("Password123");
        userPatch.getLinkedAccounts().add(new LinkedAccountPatch.Builder().linkedAccountTO(account).build());

        user = updateUser(userPatch).getEntity();
        assertEquals(1, user.getLinkedAccounts().size());

        // 3. verify that propagation task was generated and that account is found on resource
        tasks = taskService.search(
                new TaskQuery.Builder(TaskType.PROPAGATION).resource(RESOURCE_NAME_LDAP).
                        anyTypeKind(AnyTypeKind.USER).entityKey(user.getKey()).build());
        assertEquals(1, tasks.getTotalCount());
        assertEquals(connObjectName, tasks.getResult().get(0).getConnObjectKey());
        assertEquals(ResourceOperation.CREATE, tasks.getResult().get(0).getOperation());
        assertEquals(ExecStatus.SUCCESS.name(), tasks.getResult().get(0).getLatestExecStatus());

        LdapContext ldapObj = (LdapContext) getLdapRemoteObject(
                RESOURCE_LDAP_ADMIN_DN, RESOURCE_LDAP_ADMIN_PWD, connObjectName);
        assertNotNull(ldapObj);

        Attributes ldapAttrs = ldapObj.getAttributes("");
        assertEquals(
                user.getPlainAttr("email").get().getValues().get(0),
                ldapAttrs.get("mail").getAll().next().toString());
        assertEquals("LINKED_SURNAME", ldapAttrs.get("sn").getAll().next().toString());
    }
}
