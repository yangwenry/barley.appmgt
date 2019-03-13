/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing,
* software distributed under the License is distributed on an
* "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
* KIND, either express or implied.  See the License for the
* specific language governing permissions and limitations
* under the License.
*/
package barley.appmgt.gateway.handlers.security.thrift;

import java.util.ArrayList;

import barley.appmgt.api.model.AuthenticatedIDP;
import barley.appmgt.api.model.URITemplate;
import barley.appmgt.gateway.handlers.security.APISecurityConstants;
import barley.appmgt.gateway.handlers.security.APISecurityException;
import barley.appmgt.gateway.handlers.security.keys.APIKeyDataStore;
import barley.appmgt.impl.dto.APIKeyValidationInfoDTO;
import barley.appmgt.impl.AppMConstants;

public class ThriftAPIDataStore implements APIKeyDataStore{

    private static final ThriftKeyValidatorClientPool clientPool =
            ThriftKeyValidatorClientPool.getInstance();
    /**
     * Validate the given WebApp key for the specified WebApp context and version.
     *
     * @param context    Context of an WebApp
     * @param apiVersion A valid version of the WebApp
     * @param apiKey     An WebApp key string - Not necessarily a valid key
     * @return an APIKeyValidationInfoDTO instance containing key validation data
     * @throws org.wso2.carbon.appmgt.gateway.handlers.security.APISecurityException
     *          on error
     */
    public APIKeyValidationInfoDTO getAPIKeyData(String context, String apiVersion, String apiKey, String clientDomain)
            throws APISecurityException {
        ThriftKeyValidatorClient client = null;
        try {
            client = clientPool.get();
            return client.getAPIKeyData(context, apiVersion, apiKey, AppMConstants.AUTH_APPLICATION_OR_USER_LEVEL_TOKEN, clientDomain);
        } catch (Exception e) {
            throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR,
                    "Error while accessing backend services for WebApp key validation", e);
        } finally {
            try {
                if (client != null) {
                    clientPool.release(client);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Validate the given WebApp key for the specified WebApp context and version.
     *
     * @param context    Context of an WebApp
     * @param apiVersion A valid version of the WebApp
     * @param apiKey     An WebApp key string - Not necessarily a valid key
     * @return an APIKeyValidationInfoDTO instance containing key validation data
     * @throws org.wso2.carbon.appmgt.gateway.handlers.security.APISecurityException
     *          on error
     */
    public APIKeyValidationInfoDTO getAPIKeyData(String context, String apiVersion, String apiKey,
                                                 String requiredAuthenticationLevel, String clientDomain) throws APISecurityException {
        ThriftKeyValidatorClient client = null;
        try {
            client = clientPool.get();
            return client.getAPIKeyData(context, apiVersion, apiKey,requiredAuthenticationLevel, clientDomain);
        } catch (Exception e) {
            throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR,
                    "Error while accessing backend services for WebApp key validation", e);
        } finally {
            try {
                if (client != null) {
                    clientPool.release(client);
                }
            } catch (Exception ignored) {
            }
        }
    }
    public ArrayList<URITemplate> getAllURITemplates(String context, String apiVersion)
            throws APISecurityException {
        ThriftKeyValidatorClient client = null;
        try {
            client = clientPool.get();
            return client.getAllURITemplates(context, apiVersion);
        } catch (Exception e) {
            throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR,
                                           "Error while accessing backend services for WebApp key validation", e);
        } finally {
            try {
                if (client != null) {
                    clientPool.release(client);
                }
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * Clean up any resources allocated to this WebApp key data store instance.
     */
    public void cleanup() {
        
    }

	public APIKeyValidationInfoDTO getAPPData(String apiContext, String apiVersion, String consumer, AuthenticatedIDP[] authenticatedIDPs) throws APISecurityException {
	    // TODO Auto-generated method stub
	    return null;
    }
}
