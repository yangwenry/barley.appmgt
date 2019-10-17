/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package barley.appmgt.impl.utils;

import java.util.ArrayList;
import java.util.List;

import org.apache.axis2.AxisFault;
import org.apache.commons.lang.StringUtils;
import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;

import com.google.gson.Gson;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.APIIdentifier;
import barley.appmgt.impl.dto.Environment;
import barley.appmgt.impl.stub.WebAppData;
import barley.appmgt.impl.template.APITemplateBuilder;
import barley.appmgt.impl.template.APITemplateException;
import barley.core.MultitenantConstants;

public class AppGatewayAdminRestClient extends AbstractAPIGatewayAdminClient {
    private String qualifiedName;
    private String qualifiedDefaultApiName;
    private Environment environment;
    private String baseUrl;

    public AppGatewayAdminRestClient(APIIdentifier apiId, Environment environment) throws AppManagementException {
        String providerName = AppManagerUtil.replaceEmailDomain(apiId.getProviderName());
        String appName = apiId.getApiName();
        String appVersion = apiId.getVersion();
        this.qualifiedName = providerName + "--" + appName+ ":v" + appVersion;
        this.qualifiedDefaultApiName = providerName + "--" + appName;
        this.environment = environment;
        this.baseUrl = this.environment.getApiServiceEndpoint() + "/api/apiMgt";
    }

    /**
     * Add versioned web app configuration to the gateway.
     *
     * @param builder
     * @param tenantDomain
     * @throws AppManagementException on errors.
     */
    public void addVersionedWebApp(APITemplateBuilder builder, APIIdentifier apiId, String tenantDomain)
            throws AppManagementException {
        String appName = apiId.getApiName();
        String appVersion = apiId.getVersion();
        try {
            String apiConfig = builder.getConfigStringForVersionedWebAppTemplate(environment);
            List<NameValuePair> urlParams = new ArrayList<NameValuePair>(1);
	        urlParams.add(new BasicNameValuePair("apiData", apiConfig));
	        urlParams.add(new BasicNameValuePair("tenantDomain", tenantDomain));
	        
	        if (!StringUtils.isEmpty(tenantDomain) && !tenantDomain.equals(MultitenantConstants
                                                                                  .SUPER_TENANT_DOMAIN_NAME)) {
            	HttpGatewayUtils.doPost(this.baseUrl + "/addApiForTenant", urlParams);
            } else {
            	HttpGatewayUtils.doPost(this.baseUrl + "/addApiFromString", urlParams);
            }
        } catch (APITemplateException e) {
            String errorMsg = "Error while adding new WebApp. App Name : " + appName + " App Version: " + appVersion;
            throw new AppManagementException(errorMsg, e);
        } catch (AppManagementException e) {
            String errorMsg = "Error while obtaining WebApp information from gateway. App Name : " + appName + " App " +
                    "Version : " + appVersion;
            throw new AppManagementException(errorMsg, e);
        }
    }
    
    /**
     * Add non-versioned web app configuration to the gateway.
     *
     * @param builder
     * @param tenantDomain
     * @throws AppManagementException on errors.
     */
    public void addNonVersionedWebApp(APITemplateBuilder builder, APIIdentifier apiId, String tenantDomain)
            throws AppManagementException {
    	String appName = apiId.getApiName();
        String appVersion = apiId.getVersion();
        try {
        	String apiConfig = builder.getConfigStringForNonVersionedWebAppTemplate();
            List<NameValuePair> urlParams = new ArrayList<NameValuePair>(1);
	        urlParams.add(new BasicNameValuePair("apiData", apiConfig));
	        urlParams.add(new BasicNameValuePair("tenantDomain", tenantDomain));
	        
	        if (!StringUtils.isEmpty(tenantDomain) && !tenantDomain.equals(MultitenantConstants
                                                                                  .SUPER_TENANT_DOMAIN_NAME)) {
            	HttpGatewayUtils.doPost(this.baseUrl + "/addApiForTenant", urlParams);
            } else {
            	HttpGatewayUtils.doPost(this.baseUrl + "/addApiFromString", urlParams);
            }
        } catch (APITemplateException e) {
            String errorMsg = "Error while adding new WebApp. App Name : " + appName + " App Version: " + appVersion;
            throw new AppManagementException(errorMsg, e);
        } catch (AppManagementException e) {
            String errorMsg = "Error while obtaining WebApp information from gateway. App Name : " + appName + " App " +
                    "Version : " + appVersion;
            throw new AppManagementException(errorMsg, e);
        }
    }
    
    /**
     * Update versioned web app configuration in the gateway
     *
     * @param builder
     * @param tenantDomain
     * @throws AxisFault
     */
    public void updateVersionedWebApp(APITemplateBuilder builder, APIIdentifier apiId, String tenantDomain)
            throws AppManagementException {
    	String appName = apiId.getApiName();
        String appVersion = apiId.getVersion();
        try {
        	String apiConfig = builder.getConfigStringForVersionedWebAppTemplate(environment);
            List<NameValuePair> urlParams = new ArrayList<NameValuePair>(1);
            urlParams.add(new BasicNameValuePair("apiName", qualifiedName));
	        urlParams.add(new BasicNameValuePair("apiData", apiConfig));
	        urlParams.add(new BasicNameValuePair("tenantDomain", tenantDomain));
	        
	        if (!StringUtils.isEmpty(tenantDomain) && !tenantDomain.equals(MultitenantConstants
                                                                                  .SUPER_TENANT_DOMAIN_NAME)) {
            	HttpGatewayUtils.doPost(this.baseUrl + "/updateApiForTenant", urlParams);
            } else {
            	HttpGatewayUtils.doPost(this.baseUrl + "/updateApiFromString", urlParams);
            }
        } catch (APITemplateException e) {
            String errorMsg = "Error while adding new WebApp. App Name : " + appName + " App Version: " + appVersion;
            throw new AppManagementException(errorMsg, e);
        } catch (AppManagementException e) {
            String errorMsg = "Error while obtaining WebApp information from gateway. App Name : " + appName + " App " +
                    "Version : " + appVersion;
            throw new AppManagementException(errorMsg, e);
        }
    }
    
    /**
     * Update non-versioned web app configuration in the gateway
     *
     * @param builder
     * @param tenantDomain
     * @throws AppManagementException on errors.
     */
    public void updateNonVersionedWebApp(APITemplateBuilder builder, APIIdentifier apiId, String tenantDomain)
            throws AppManagementException {
    	String appName = apiId.getApiName();
        String appVersion = apiId.getVersion();
        try {
        	String apiConfig = builder.getConfigStringForNonVersionedWebAppTemplate();
            List<NameValuePair> urlParams = new ArrayList<NameValuePair>(1);
            urlParams.add(new BasicNameValuePair("apiName", qualifiedDefaultApiName));
	        urlParams.add(new BasicNameValuePair("apiData", apiConfig));
	        urlParams.add(new BasicNameValuePair("tenantDomain", tenantDomain));
	        
	        if (!StringUtils.isEmpty(tenantDomain) && !tenantDomain.equals(MultitenantConstants
                                                                                  .SUPER_TENANT_DOMAIN_NAME)) {
            	HttpGatewayUtils.doPost(this.baseUrl + "/updateApiForTenant", urlParams);
            } else {
            	HttpGatewayUtils.doPost(this.baseUrl + "/updateApiFromString", urlParams);
            }
        } catch (APITemplateException e) {
            String errorMsg = "Error while adding new WebApp. App Name : " + appName + " App Version: " + appVersion;
            throw new AppManagementException(errorMsg, e);
        } catch (AppManagementException e) {
            String errorMsg = "Error while obtaining WebApp information from gateway. App Name : " + appName + " App " +
                    "Version : " + appVersion;
            throw new AppManagementException(errorMsg, e);
        }
    }
    
    /**
     * Delete versioned web app configuration from the gateway
     *
     * @param tenantDomain
     * @throws AppManagementException on errors.
     */
    public void deleteVersionedWebApp(APIIdentifier apiId, String tenantDomain) throws AppManagementException {
    	String appName = apiId.getApiName();
        String appVersion = apiId.getVersion();
        try {
        	List<NameValuePair> urlParams = new ArrayList<NameValuePair>(1);
        	urlParams.add(new BasicNameValuePair("apiName", qualifiedName));
	        urlParams.add(new BasicNameValuePair("tenantDomain", tenantDomain));
	        
	        if (!StringUtils.isEmpty(tenantDomain) && !tenantDomain.equals(MultitenantConstants
                                                                                  .SUPER_TENANT_DOMAIN_NAME)) {
            	HttpGatewayUtils.doPost(this.baseUrl + "/deleteApiForTenant", urlParams);
            } else {
            	HttpGatewayUtils.doPost(this.baseUrl + "/deleteApi", urlParams);
            }
        } catch (AppManagementException e) {
            String errorMsg = "Error while obtaining WebApp information from gateway. App Name : " + appName + " App " +
                    "Version : " + appVersion;
            throw new AppManagementException(errorMsg, e);
        }
    }
    
    /**
     * Delete non-versioned web app configuration form the gateway
     *
     * @param tenantDomain
     * @throws AppManagementException on errors.
     */
    public void deleteNonVersionedWebApp(APIIdentifier apiId, String tenantDomain) throws AppManagementException {
    	String appName = apiId.getApiName();
        String appVersion = apiId.getVersion();
        try {
        	List<NameValuePair> urlParams = new ArrayList<NameValuePair>(1);
        	urlParams.add(new BasicNameValuePair("apiName", qualifiedDefaultApiName));
	        urlParams.add(new BasicNameValuePair("tenantDomain", tenantDomain));
	        
	        if (!StringUtils.isEmpty(tenantDomain) && !tenantDomain.equals(MultitenantConstants
                                                                                  .SUPER_TENANT_DOMAIN_NAME)) {
            	HttpGatewayUtils.doPost(this.baseUrl + "/deleteApiForTenant", urlParams);
            } else {
            	HttpGatewayUtils.doPost(this.baseUrl + "/deleteApi", urlParams);
            }
        } catch (AppManagementException e) {
            String errorMsg = "Error while obtaining WebApp information from gateway. App Name : " + appName + " App " +
                    "Version : " + appVersion;
            throw new AppManagementException(errorMsg, e);
        }
    }

    /**
     * Return versioned web app configuration from the gateway.
     *
     * @param tenantDomain
     * @return
     * @throws AppManagementException on errors.
     */
    public WebAppData getVersionedWebApp(APIIdentifier apiId, String tenantDomain) throws AppManagementException {
        String appName = apiId.getApiName();
        String appVersion = apiId.getVersion();
        WebAppData appData = null;
        String entityStr;
        try {
        	List<NameValuePair> urlParams = new ArrayList<NameValuePair>(1);
	        urlParams.add(new BasicNameValuePair("apiName", qualifiedName));
	        urlParams.add(new BasicNameValuePair("tenantDomain", tenantDomain));
        	
        	if (!StringUtils.isEmpty(tenantDomain) && !tenantDomain.equals(MultitenantConstants
                                                                                  .SUPER_TENANT_DOMAIN_NAME)) {
            	entityStr = HttpGatewayUtils.receive(this.baseUrl + "/getApiForTenant", urlParams);
            } else {
            	entityStr = HttpGatewayUtils.receive(this.baseUrl + "/getApiByName", urlParams);
            }
            if(entityStr != null) {
            	appData = new Gson().fromJson(entityStr, WebAppData.class);
            }
            return appData;
        } catch (AppManagementException e) {
            String errorMsg = "Error while obtaining WebApp information from gateway. App Name : " + appName + " App " +
                    "Version : " + appVersion;
            throw new AppManagementException(errorMsg, e);
        }
    }
    
    /**
     * Return the non-versioned web app configuration from the gateway.
     *
     * @param tenantDomain
     * @return
     * @throws AppManagementException on errors.
     */
    public WebAppData getNonVersionedWebAppData(APIIdentifier apiId, String tenantDomain) throws AppManagementException {
    	String appName = apiId.getApiName();
        String appVersion = apiId.getVersion();
        WebAppData appData = null;
        String entityStr;
        try {
        	List<NameValuePair> urlParams = new ArrayList<NameValuePair>(1);
	        urlParams.add(new BasicNameValuePair("apiName", qualifiedDefaultApiName));
	        urlParams.add(new BasicNameValuePair("tenantDomain", tenantDomain));
        	
        	if (!StringUtils.isEmpty(tenantDomain) && !tenantDomain.equals(MultitenantConstants
                                                                                  .SUPER_TENANT_DOMAIN_NAME)) {
            	entityStr = HttpGatewayUtils.receive(this.baseUrl + "/getApiForTenant", urlParams);
            } else {
            	entityStr = HttpGatewayUtils.receive(this.baseUrl + "/getApiByName", urlParams);
            }
            if(entityStr != null) {
            	appData = new Gson().fromJson(entityStr, WebAppData.class);
            }
            return appData;
        } catch (AppManagementException e) {
            String errorMsg = "Error while obtaining WebApp information from gateway. App Name : " + appName + " App " +
                    "Version : " + appVersion;
            throw new AppManagementException(errorMsg, e);
        }
    }
    
}