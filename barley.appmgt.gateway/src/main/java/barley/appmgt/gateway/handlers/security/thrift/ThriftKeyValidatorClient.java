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
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.thrift.protocol.TBinaryProtocol;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TSSLTransportFactory;
import org.apache.thrift.transport.TTransport;
import org.apache.thrift.transport.TTransportException;

import barley.appmgt.gateway.handlers.security.APISecurityConstants;
import barley.appmgt.gateway.handlers.security.APISecurityException;
import barley.appmgt.api.model.URITemplate;
import barley.appmgt.impl.dto.APIKeyValidationInfoDTO;

public class ThriftKeyValidatorClient {

    private ThriftUtils thriftUtils = null;
    private String sessionId = null;
    private static final Log log = LogFactory.getLog(ThriftKeyValidatorClientPool.class);
    private APIKeyValidationService.Client keyValClient = null;

    public ThriftKeyValidatorClient() throws APISecurityException {
        try {
            thriftUtils = ThriftUtils.getInstance();
            sessionId = thriftUtils.getSessionId();
            //create new APIKeyValidator client
            TSSLTransportFactory.TSSLTransportParameters param = new TSSLTransportFactory.TSSLTransportParameters();

            param.setTrustStore(thriftUtils.getTrustStorePath(), thriftUtils.getTrustStorePassword());

            TTransport transport = TSSLTransportFactory.getClientSocket(
                    thriftUtils.getThriftServerHost(), thriftUtils.getThriftPort(),
                    thriftUtils.getThriftClientConnectionTimeOut(), param);

            //TProtocol protocol = new TCompactProtocol(transport);
            //TODO:needs to decide on the optimum protocol.
            TProtocol protocol = new TBinaryProtocol(transport);

            //create thrift based entitlement service client and invoke
            keyValClient = new APIKeyValidationService.Client(protocol);

        } catch (TTransportException e) {
            throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR, e.getMessage());
        }
    }

    public APIKeyValidationInfoDTO getAPIKeyData(String context, String apiVersion,
                                                 String apiKey,String requiredAuthenticationLevel,
                                                 String clientDomain) throws APISecurityException {
        APIKeyValidationInfoDTO apiKeyValidationInfoDTO = null;
        barley.appmgt.gateway.handlers.security.thrift.APIKeyValidationInfoDTO thriftDTO;

        try {
            thriftDTO = keyValClient.validateKey(context, apiVersion, apiKey, sessionId,requiredAuthenticationLevel,clientDomain);

        } catch (Exception e) {
            try {

                log.warn("Login failed.. Authenticating again..");
                sessionId = thriftUtils.reLogin();
                //we re-initialize the thrift client in case open sockets have been closed due to
                //key manager restart.
                reInitializeClient();
                thriftDTO = keyValClient.validateKey(context, apiVersion, apiKey, sessionId,requiredAuthenticationLevel, clientDomain);

            } catch (Exception e1) {
                throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR, e1.getMessage());
            }
        }
        //do the conversion other side
        apiKeyValidationInfoDTO = new APIKeyValidationInfoDTO();
        apiKeyValidationInfoDTO.setApplicationName(thriftDTO.getApplicationName());
        apiKeyValidationInfoDTO.setAuthorized(thriftDTO.isAuthorized());
        apiKeyValidationInfoDTO.setEndUserName(thriftDTO.getEndUserName());
        apiKeyValidationInfoDTO.setEndUserToken(thriftDTO.getEndUserToken());
        apiKeyValidationInfoDTO.setSubscriber(thriftDTO.getSubscriber());
        apiKeyValidationInfoDTO.setTier(thriftDTO.getTier());
        apiKeyValidationInfoDTO.setType(thriftDTO.getType());
        apiKeyValidationInfoDTO.setValidationStatus(thriftDTO.getValidationStatus());
        apiKeyValidationInfoDTO.setApplicationId(thriftDTO.getApplicationId());
        apiKeyValidationInfoDTO.setApplicationTier(thriftDTO.getApplicationTier());
        apiKeyValidationInfoDTO.setApiName(thriftDTO.getApiName());
        apiKeyValidationInfoDTO.setApiPublisher(thriftDTO.getApiPublisher());
        apiKeyValidationInfoDTO.setConsumerKey(thriftDTO.getConsumerKey());

        return apiKeyValidationInfoDTO;
    }
    public ArrayList<URITemplate> getAllURITemplates(String context, String apiVersion
    ) throws APISecurityException {
        ArrayList<URITemplate> templates=new ArrayList<URITemplate>();
        List<barley.appmgt.gateway.handlers.security.thrift.URITemplate> uriTemplates;

        try {
            uriTemplates = keyValClient.getAllURITemplates(context, apiVersion,sessionId);

        } catch (Exception e) {
            try {

                log.warn("Login failed.. Authenticating again..");
                sessionId = thriftUtils.reLogin();
                //we re-initialize the thrift client in case open sockets have been closed due to
                //key manager restart.
                reInitializeClient();
                uriTemplates = keyValClient.getAllURITemplates(context, apiVersion, sessionId);

            } catch (Exception e1) {
                throw new APISecurityException(APISecurityConstants.API_AUTH_GENERAL_ERROR, e1.getMessage());
            }
        }
        for (barley.appmgt.gateway.handlers.security.thrift.URITemplate aDto : uriTemplates) {
            URITemplate temp = toTemplates(aDto);
            templates.add(temp);
        }
        return templates;
    }

    private URITemplate toTemplates(
    		barley.appmgt.gateway.handlers.security.thrift.URITemplate dto) {
        URITemplate template = new URITemplate();
        template.setAuthType(dto.getAuthType());
        template.setHTTPVerb(dto.getHttpVerb());
        template.setResourceSandboxURI(dto.getResourceSandboxURI());
        template.setUriTemplate(dto.getUriTemplate());
        template.setThrottlingTier(dto.getThrottlingTier());
        return template;
    }


    private void reInitializeClient() throws APISecurityException, TTransportException {
            //create new APIKeyValidator client
            TSSLTransportFactory.TSSLTransportParameters param = new TSSLTransportFactory.TSSLTransportParameters();

            param.setTrustStore(thriftUtils.getTrustStorePath(), thriftUtils.getTrustStorePassword());

            TTransport transport = TSSLTransportFactory.getClientSocket(
                    thriftUtils.getRemoteServerIP(), thriftUtils.getThriftPort(),
                    thriftUtils.getThriftClientConnectionTimeOut(), param);

            //TProtocol protocol = new TCompactProtocol(transport);
            //TODO:needs to decide on the optimum protocol.
            TProtocol protocol = new TBinaryProtocol(transport);

            //create thrift based entitlement service client and invoke
            keyValClient = new APIKeyValidationService.Client(protocol);
    }
}
