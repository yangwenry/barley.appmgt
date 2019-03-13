
/*
*  Copyright (c) 2005-2011, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package barley.appmgt.impl.dto;

import java.io.Serializable;
import java.util.List;

/**
 * This class represent the WebApp key validation Info DTO.
 */
public class APIKeyValidationInfoDTO implements Serializable {

    private static final long serialVersionUID = 12345L;

    private boolean authorized;
    private String subscriber;
    private int subscriptionId;
    private String trustedIdp;
    private String tier;
    private String type;
    //JWT or SAML token containing details of WebApp invoker
    private String userType;
    private String endUserToken;
    private String endUserName;
    private String applicationId;
    private String applicationName;
    private String applicationTier;
    //use this to pass key validation status
    private int validationStatus;
    private long validityPeriod;
    private long issuedTime;
    private List<String> authorizedDomains;

    private String apiName;
    private String apiVersion;
    private String context;
    private String apiPublisher;
    private String logoutURL;

    public String getLogoutURL() {
        return logoutURL;
    }

    public void setLogoutURL(String logoutURL) {
        this.logoutURL = logoutURL;
    }

    private String consumerKey;

    public boolean isAuthorized() {
        return authorized;
    }

    public void setAuthorized(boolean authorized) {
        this.authorized = authorized;
    }

    public String getTier() {
        return tier;
    }

    public void setTier(String tier) {
        this.tier = tier;
    }

    public String getSubscriber() {
        return subscriber;
    }

	public void setSubscriber(String subscriber) {
		this.subscriber = subscriber;
	}

	public int getSubscriptionId() {
		return subscriptionId;
	}

	public void setSubscriptionId(int subscriptionId) {
		this.subscriptionId = subscriptionId;
	}

	public String getTrustedIdp() {
		return trustedIdp;
	}

	public void setTrustedIdp(String trustedIdp) {
		this.trustedIdp = trustedIdp;
	}

	public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getEndUserToken() {
        return endUserToken;
    }

    public void setEndUserToken(String endUserToken) {
        this.endUserToken = endUserToken;
    }

    public String getEndUserName() {
        return endUserName;
    }

    public void setEndUserName(String endUserName) {
        this.endUserName = endUserName;
    }

    public String getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(String applicationId) {
        this.applicationId = applicationId;
    }

    public String getApplicationName() {
        return applicationName;
    }

    public void setApplicationName(String applicationName) {
        this.applicationName = applicationName;
    }

    public String getApplicationTier() {
        return applicationTier;
    }

    public void setApplicationTier(String applicationTier) {
        this.applicationTier = applicationTier;
    }

    public int getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(int validationStatus) {
        this.validationStatus = validationStatus;
    }

    public long getValidityPeriod() {
        return validityPeriod;
    }

    public void setValidityPeriod(long validityPeriod) {
        this.validityPeriod = validityPeriod;
    }

    public long getIssuedTime() {
        return issuedTime;
    }

    public void setIssuedTime(long issuedTime) {
        this.issuedTime = issuedTime;
    }
    
    public List<String> getAuthorizedDomains() {
		return authorizedDomains;
	}

	public void setAuthorizedDomains(List<String> authorizedDomains) {
		this.authorizedDomains = authorizedDomains;
	}

	public String getUserType() {
        return userType;
    }

    public void setUserType(String userType) {
        this.userType = userType;
    }

    public String getApiName() {
        return apiName;
    }

    public void setApiName(String apiName) {
        this.apiName = apiName;
    }

    public String getApiVersion() {
        return apiVersion;
    }

    public void setApiVersion(String apiVersion) {
        this.apiVersion = apiVersion;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    public String getConsumerKey() {
        return consumerKey;
    }

    public void setConsumerKey(String consumerKey) {
        this.consumerKey = consumerKey;
    }

    public String getApiPublisher() {
        return apiPublisher;
    }

    public void setApiPublisher(String apiPublisher) {
        this.apiPublisher = apiPublisher;
    }
}

