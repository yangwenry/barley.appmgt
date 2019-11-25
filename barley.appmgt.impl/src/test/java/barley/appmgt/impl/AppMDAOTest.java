/*
 *  Copyright WSO2 Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package barley.appmgt.impl;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicNameValuePair;

import barley.appmgt.api.APIProvider;
import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.APIIdentifier;
import barley.appmgt.api.model.APIStatus;
import barley.appmgt.api.model.BusinessOwner;
import barley.appmgt.api.model.Provider;
import barley.appmgt.api.model.Tier;
import barley.appmgt.api.model.URITemplate;
import barley.appmgt.api.model.WebApp;
import barley.appmgt.impl.dao.AppMDAO;
import barley.appmgt.impl.internal.AppManagerComponent;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.APIMgtDBUtil;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.appmgt.impl.utils.HttpGatewayUtils;
import barley.core.BarleyConstants;
import barley.core.utils.BarleyUtils;
import barley.identity.core.util.IdentityConfigParser;
import barley.identity.core.util.IdentityTenantUtil;
import barley.registry.core.service.TenantRegistryLoader;
import barley.registry.core.utils.UUIDGenerator;
import barley.registry.indexing.service.TenantIndexingLoader;

public class AppMDAOTest extends BaseTestCase {

    private static final Log log = LogFactory.getLog(AppMDAOTest.class);
    
    APIProvider provider = null;
    String providerName = null;
    
    public void setUp() throws Exception {
    	super.setupCarbonHome();
    	String dbConfigPath = System.getProperty("AppManagerDBConfigurationPath");
		AppManagerConfiguration config = new AppManagerConfiguration();
		config.load(dbConfigPath);
		ServiceReferenceHolder.getInstance()
		                      .setAPIManagerConfigurationService(new AppManagerConfigurationServiceImpl(config));
    	APIMgtDBUtil.initialize();
    }
    
    public void testAddPolicy() throws AppManagementException {
    	String policyGroupName = "lemon";
    	String throttlingTier = "Unlimited";
    	String userRoles = "admin";
    	String isAnonymousAllowed = "true";
    	Object[] objPartialMappings = null;
    	String policyGroupDesc = "";
//    	AppMDAO.savePolicyGroup(policyGroupName, throttlingTier, userRoles, isAnonymousAllowed, objPartialMappings, policyGroupDesc);
    	
    	policyGroupName = "my-policy";
    	int policyGroupId = 1;
    	String authorizedAdminCookie = "";
    	AppMDAO.updatePolicyGroup(policyGroupName, throttlingTier, userRoles, isAnonymousAllowed, 
    			policyGroupId, objPartialMappings, policyGroupDesc, authorizedAdminCookie);
    }
    
    
    public void testHttpClient() throws AppManagementException {
//    	HttpClient endpointClient = AppManagerUtil.getHttpClient(0, null);
//    	Assert.isNotNull(endpointClient);
    	
    	String endpoint = "http://demo.app.taac.co.kr:80/apiMgt/getApiByName";
//    	String endpoint = "http://localhost:9020/apiMgt/getApiByName";
    	List urlParams = new ArrayList();
    	BasicNameValuePair nv = new BasicNameValuePair("apiName", "n3");
    	urlParams.add(nv);
    	String response = HttpGatewayUtils.receive(endpoint, urlParams);
    	//Assert.isNotNull(response);
    }
    
    
}


