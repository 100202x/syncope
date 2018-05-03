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
package org.apache.syncope.core.logic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.impl.RandomBasedGenerator;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.ws.rs.core.MediaType;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.apache.cxf.jaxrs.client.WebClient;
import org.apache.cxf.jaxrs.provider.json.JsonMapObjectProvider;
import org.apache.cxf.rs.security.jose.jaxrs.JsonWebKeysProvider;
import org.apache.cxf.rs.security.oauth2.client.Consumer;
import org.apache.cxf.rs.security.oauth2.common.ClientAccessToken;
import org.apache.cxf.rs.security.oidc.common.IdToken;
import org.apache.cxf.rs.security.oidc.common.UserInfo;
import org.apache.cxf.rs.security.oidc.rp.IdTokenReader;
import org.apache.cxf.rs.security.oidc.rp.UserInfoClient;
import org.apache.syncope.common.lib.AbstractBaseBean;
import org.apache.syncope.common.lib.OIDCConstants;
import org.apache.syncope.common.lib.SyncopeClientException;
import org.apache.syncope.common.lib.to.AttrTO;
import org.apache.syncope.common.lib.to.OIDCLoginRequestTO;
import org.apache.syncope.common.lib.to.OIDCLoginResponseTO;
import org.apache.syncope.common.lib.types.CipherAlgorithm;
import org.apache.syncope.common.lib.types.ClientExceptionType;
import org.apache.syncope.common.lib.types.StandardEntitlement;
import org.apache.syncope.core.logic.model.TokenEndpointResponse;
import org.apache.syncope.core.logic.oidc.OIDCUserManager;
import org.apache.syncope.core.persistence.api.dao.NotFoundException;
import org.apache.syncope.core.persistence.api.dao.OIDCProviderDAO;
import org.apache.syncope.core.persistence.api.entity.OIDCProvider;
import org.apache.syncope.core.persistence.api.entity.OIDCProviderItem;
import org.apache.syncope.core.provisioning.api.data.AccessTokenDataBinder;
import org.apache.syncope.core.provisioning.api.serialization.POJOHelper;
import org.apache.syncope.core.spring.security.AuthContextUtils;
import org.apache.syncope.core.spring.security.AuthDataAccessor;
import org.apache.syncope.core.spring.security.Encryptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Component;

@Component
public class OIDCClientLogic extends AbstractTransactionalLogic<AbstractBaseBean> {

    private static final Encryptor ENCRYPTOR = Encryptor.getInstance();

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final RandomBasedGenerator UUID_GENERATOR = Generators.randomBasedGenerator();

    private static final String JWT_CLAIM_OP_ENTITYID = "OP_ENTITYID";

    private static final String JWT_CLAIM_USERID = "USERID";

    @Autowired
    private AuthDataAccessor authDataAccessor;

    @Autowired
    private AccessTokenDataBinder accessTokenDataBinder;

    @Autowired
    private OIDCProviderDAO opDAO;

    @Autowired
    private OIDCUserManager userManager;

    private OIDCProvider getOIDCProvider(final String opName) {
        OIDCProvider op = null;
        if (StringUtils.isBlank(opName)) {
            List<OIDCProvider> ops = opDAO.findAll();
            if (!ops.isEmpty()) {
                op = ops.get(0);
            }
        } else {
            op = opDAO.findByName(opName);
        }
        if (op == null) {
            throw new NotFoundException(StringUtils.isBlank(opName)
                    ? "Any OIDC Provider"
                    : "OIDC Provider '" + opName + "'");
        }
        return op;
    }

    @PreAuthorize("hasRole('" + StandardEntitlement.ANONYMOUS + "')")
    public OIDCLoginRequestTO createLoginRequest(final String redirectURI, final String opName) {
        // 1. look for Provider
        OIDCProvider op = getOIDCProvider(opName);

        // 2. create AuthnRequest
        OIDCLoginRequestTO requestTO = new OIDCLoginRequestTO();
        requestTO.setProviderAddress(op.getAuthorizationEndpoint());
        requestTO.setClientId(op.getClientID());
        requestTO.setScope("openid email profile");
        requestTO.setResponseType("code");
        requestTO.setRedirectURI(redirectURI);
        requestTO.setState(UUID_GENERATOR.generate().toString());
        return requestTO;
    }

    @PreAuthorize("hasRole('" + StandardEntitlement.ANONYMOUS + "')")
    public OIDCLoginResponseTO login(final String redirectURI, final String authorizationCode, final String opName) {
        final OIDCProvider op = getOIDCProvider(opName);

        // 1. get OpenID Connect tokens
        String body = OIDCConstants.CODE + "=" + authorizationCode
                + "&" + OIDCConstants.CLIENT_ID + "=" + op.getClientID()
                + "&" + OIDCConstants.CLIENT_SECRET + "=" + op.getClientSecret()
                + "&" + OIDCConstants.REDIRECT_URI + "=" + redirectURI
                + "&" + OIDCConstants.GRANT_TYPE + "=authorization_code";
        TokenEndpointResponse tokenEndpointResponse = getOIDCTokens(op.getTokenEndpoint(), body);

        // 1. get OpenID Connect tokens
        Consumer consumer = new Consumer(op.getClientID(), op.getClientSecret());

        // 2. validate token
        IdToken idToken = getValidatedIdToken(op, consumer, tokenEndpointResponse.getIdToken());

        // 3. extract user information
        UserInfo userInfo = getUserInfo(op, tokenEndpointResponse.getAccessToken(), idToken, consumer);

        // 4. prepare the result: find matching user (if any) and return the received attributes
        final OIDCLoginResponseTO responseTO = new OIDCLoginResponseTO();
        responseTO.setEmail(userInfo.getEmail());
        responseTO.setFamilyName(userInfo.getFamilyName());
        responseTO.setGivenName(userInfo.getGivenName());
        responseTO.setName(userInfo.getName());
        responseTO.setSubject(userInfo.getSubject());

        String keyValue = null;
        for (OIDCProviderItem item : op.getItems()) {
            AttrTO attrTO = new AttrTO();
            attrTO.setSchema(item.getExtAttrName());
            switch (item.getExtAttrName()) {
                case UserInfo.PREFERRED_USERNAME_CLAIM:
                    attrTO.getValues().add(userInfo.getPreferredUserName());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getPreferredUserName();
                    }
                    break;

                case UserInfo.PROFILE_CLAIM:
                    attrTO.getValues().add(userInfo.getProfile());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getProfile();
                    }
                    break;

                case UserInfo.EMAIL_CLAIM:
                    attrTO.getValues().add(userInfo.getEmail());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getEmail();
                    }
                    break;

                case UserInfo.NAME_CLAIM:
                    attrTO.getValues().add(userInfo.getName());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getName();
                    }
                    break;

                case UserInfo.FAMILY_NAME_CLAIM:
                    attrTO.getValues().add(userInfo.getFamilyName());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getFamilyName();
                    }
                    break;

                case UserInfo.MIDDLE_NAME_CLAIM:
                    attrTO.getValues().add(userInfo.getMiddleName());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getMiddleName();
                    }
                    break;

                case UserInfo.GIVEN_NAME_CLAIM:
                    attrTO.getValues().add(userInfo.getGivenName());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getGivenName();
                    }
                    break;

                case UserInfo.NICKNAME_CLAIM:
                    attrTO.getValues().add(userInfo.getNickName());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getNickName();
                    }
                    break;

                case UserInfo.GENDER_CLAIM:
                    attrTO.getValues().add(userInfo.getGender());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getGender();
                    }
                    break;

                case UserInfo.LOCALE_CLAIM:
                    attrTO.getValues().add(userInfo.getLocale());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getLocale();
                    }
                    break;

                case UserInfo.ZONEINFO_CLAIM:
                    attrTO.getValues().add(userInfo.getZoneInfo());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getZoneInfo();
                    }
                    break;

                case UserInfo.BIRTHDATE_CLAIM:
                    attrTO.getValues().add(userInfo.getBirthDate());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getBirthDate();
                    }
                    break;

                case UserInfo.PHONE_CLAIM:
                    attrTO.getValues().add(userInfo.getPhoneNumber());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getPhoneNumber();
                    }
                    break;

                case UserInfo.ADDRESS_CLAIM:
                    attrTO.getValues().add(userInfo.getUserAddress().getFormatted());
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = userInfo.getUserAddress().getFormatted();
                    }
                    break;

                case UserInfo.UPDATED_AT_CLAIM:
                    attrTO.getValues().add(Long.toString(userInfo.getUpdatedAt()));
                    responseTO.getAttrs().add(attrTO);
                    if (item.isConnObjectKey()) {
                        keyValue = Long.toString(userInfo.getUpdatedAt());
                    }
                    break;

                default:
                    LOG.warn("Unsupported: {} ", item.getExtAttrName());
            }
        }

        final List<String> matchingUsers = keyValue == null
                ? Collections.<String>emptyList()
                : userManager.findMatchingUser(keyValue, op.getConnObjectKeyItem());
        LOG.debug("Found {} matching users for {}", matchingUsers.size(), keyValue);

        String username;
        if (matchingUsers.isEmpty()) {
            if (op.isCreateUnmatching()) {
                LOG.debug("No user matching {}, about to create", keyValue);

                final String emailValue = userInfo.getEmail();
                username = AuthContextUtils.execWithAuthContext(
                        AuthContextUtils.getDomain(), new AuthContextUtils.Executable<String>() {

                    @Override
                    public String exec() {
                        return userManager.create(op, responseTO, emailValue);
                    }
                });
            } else {
                throw new NotFoundException("User matching the provided value " + keyValue);
            }
        } else if (matchingUsers.size() > 1) {
            throw new IllegalArgumentException("Several users match the provided value " + keyValue);
        } else {
            if (op.isUpdateMatching()) {
                LOG.debug("About to update {} for {}", matchingUsers.get(0), keyValue);

                username = AuthContextUtils.execWithAuthContext(
                        AuthContextUtils.getDomain(), new AuthContextUtils.Executable<String>() {

                    @Override
                    public String exec() {
                        return userManager.update(matchingUsers.get(0), op, responseTO);
                    }
                });
            } else {
                username = matchingUsers.get(0);
            }

        }

        responseTO.setUsername(username);

        // 5. generate JWT for further access
        Map<String, Object> claims = new HashMap<>();
        claims.put(JWT_CLAIM_OP_ENTITYID, idToken.getIssuer());
        claims.put(JWT_CLAIM_USERID, idToken.getSubject());

        byte[] authorities = null;
        try {
            authorities = ENCRYPTOR.encode(POJOHelper.serialize(
                    authDataAccessor.getAuthorities(responseTO.getUsername())), CipherAlgorithm.AES).
                    getBytes();
        } catch (Exception e) {
            LOG.error("Could not fetch authorities", e);
        }

        Pair<String, Date> accessTokenInfo =
                accessTokenDataBinder.create(responseTO.getUsername(), claims, authorities, true);
        responseTO.setAccessToken(accessTokenInfo.getLeft());
        responseTO.setAccessTokenExpiryTime(accessTokenInfo.getRight());

        return responseTO;
    }

    private TokenEndpointResponse getOIDCTokens(final String url, final String body) {
        String oidcTokens = WebClient.create(url).
                type(MediaType.APPLICATION_FORM_URLENCODED).accept(MediaType.APPLICATION_JSON).
                post(body).
                readEntity(String.class);
        TokenEndpointResponse endpointResponse = null;
        try {
            endpointResponse = MAPPER.readValue(oidcTokens, TokenEndpointResponse.class);
        } catch (Exception e) {
            LOG.error("While getting the Tokens from the OP", e);
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.Unknown);
            sce.getElements().add(e.getMessage());
            throw sce;
        }
        return endpointResponse;
    }

    private IdToken getValidatedIdToken(final OIDCProvider op, final Consumer consumer, final String jwtIdToken) {
        IdTokenReader idTokenReader = new IdTokenReader();
        idTokenReader.setClockOffset(10);
        idTokenReader.setIssuerId(op.getIssuer());
        WebClient jwkSetClient = WebClient.create(
                op.getJwksUri(), Arrays.asList(new JsonWebKeysProvider())).
                accept(MediaType.APPLICATION_JSON);
        idTokenReader.setJwkSetClient(jwkSetClient);
        IdToken idToken = null;
        try {
            idToken = idTokenReader.getIdToken(jwtIdToken, consumer);
        } catch (Exception e) {
            LOG.error("While validating the id_token", e);
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.Unknown);
            sce.getElements().add(e.getMessage());
            throw sce;
        }
        return idToken;
    }

    private UserInfo getUserInfo(
            final OIDCProvider op,
            final String accessToken,
            final IdToken idToken,
            final Consumer consumer) {

        WebClient userInfoServiceClient = WebClient.create(
                op.getUserinfoEndpoint(), Arrays.asList(new JsonMapObjectProvider())).
                accept(MediaType.APPLICATION_JSON);
        ClientAccessToken clientAccessToken = new ClientAccessToken("Bearer", accessToken);
        UserInfoClient userInfoClient = new UserInfoClient();
        userInfoClient.setUserInfoServiceClient(userInfoServiceClient);
        UserInfo userInfo = null;
        try {
            userInfo = userInfoClient.getUserInfo(clientAccessToken, idToken, consumer);
        } catch (Exception e) {
            LOG.error("While getting the userInfo", e);
            SyncopeClientException sce = SyncopeClientException.build(ClientExceptionType.Unknown);
            sce.getElements().add(e.getMessage());
            throw sce;
        }
        return userInfo;
    }

    @Override
    protected AbstractBaseBean resolveReference(
            final Method method, final Object... args) throws UnresolvedReferenceException {

        throw new UnresolvedReferenceException();
    }
}
