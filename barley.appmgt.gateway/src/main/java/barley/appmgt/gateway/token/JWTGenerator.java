/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package barley.appmgt.gateway.token;

import java.util.Calendar;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeSet;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.token.ClaimsRetriever;
import barley.core.MultitenantConstants;
import barley.core.context.BarleyContext;
import barley.core.multitenancy.MultitenantUtils;
import barley.user.api.UserStoreException;

/**
 * This class represents the JSON Web Token generator default implementation.
 * By default the following properties are encoded to each authenticated WebApp request:
 * subscriber, applicationName, apiContext, version, tier, and endUserName
 * Additional properties can be encoded by engaging the ClaimsRetrieverImplClass callback-handler.
 * The JWT header and body are base64 encoded separately and concatenated with a dot.
 * Finally the token is signed using SHA256 with RSA algorithm.
 */
public class JWTGenerator extends AbstractJWTGenerator {

    private static final Log log = LogFactory.getLog(JWTGenerator.class);

    public Map<String, String> populateCustomClaims(Map<String, Object> saml2Assertions)
            throws AppManagementException {

        Map<String, String> claims = new LinkedHashMap<String, String>();
        populateIssuerAndExpiry(claims);
        ClaimsRetriever claimsRetriever = getClaimsRetriever();
        if (claimsRetriever != null) {
            String userName = (String) saml2Assertions.get("Subject");
            String tenantDomain = BarleyContext.getThreadLocalCarbonContext().getTenantDomain();
            String tenantAwareUserName = userName.contains("@") ? userName : userName + "@" + tenantDomain;

            try {
                int tenantId = ServiceReferenceHolder.getInstance().getRealmService()
                        .getTenantManager().getTenantId(tenantDomain);
                if (MultitenantConstants.SUPER_TENANT_ID == tenantId) {
                    tenantAwareUserName = MultitenantUtils.getTenantAwareUsername(tenantAwareUserName);
                }

                claims.put("sub", userName);
                SortedMap<String, String> userClaims = claimsRetriever.getClaims(tenantAwareUserName);

                if(userClaims != null){
                    claims.putAll(userClaims);
                }

                return claims;
            } catch (UserStoreException e) {
                log.error("Error while getting tenant id to populate claims ", e);
                throw new AppManagementException("Error while getting tenant id to populate claims ", e);
            }
        }
        return null;
    }

    public Map<String, String> populateStandardClaims(Map<String, Object> saml2Assertions)
            throws AppManagementException {

        Map<String, String> claims = new LinkedHashMap<String, String>();
        populateIssuerAndExpiry(claims);
        populateSaml2Assertions(claims, saml2Assertions);
        return claims;
    }

    private void populateIssuerAndExpiry(Map<String, String> claims) {
        long currentTime = Calendar.getInstance().getTimeInMillis();
        long expireIn = currentTime + 1000 * 60 * getTTL();

        claims.put("iss", APP_GATEWAY_ID);
        claims.put("exp", String.valueOf(expireIn));
    }

    private void populateSaml2Assertions(Map<String, String> claims, Map<String, Object> saml2Assertions) {
        Iterator<String> it = new TreeSet(saml2Assertions.keySet()).iterator();
        while (it.hasNext()) {
            String assertionAttribute = it.next();
            claims.put(assertionAttribute, saml2Assertions.get(assertionAttribute).toString());
        }
    }
}