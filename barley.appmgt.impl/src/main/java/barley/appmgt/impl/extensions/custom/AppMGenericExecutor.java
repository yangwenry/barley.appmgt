/*
 *
 *  * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *  *
 *  * WSO2 Inc. licenses this file to you under the Apache License,
 *  * Version 2.0 (the "License"); you may not use this file except
 *  * in compliance with the License.
 *  * you may obtain a copy of the License at
 *  *
 *  *   http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing,
 *  * software distributed under the License is distributed on an
 *  * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  * KIND, either express or implied.  See the License for the
 *  * specific language governing permissions and limitations
 *  * under the License.
 *
 */

package barley.appmgt.impl.extensions.custom;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
//import org.wso2.jaggery.scxml.management.DynamicValueInjector;
//import org.wso2.jaggery.scxml.management.StateExecutor;
//import org.wso2.jaggery.scxml.threading.JaggeryThreadLocalMediator;
//import org.wso2.jaggery.scxml.threading.contexts.JaggeryThreadContext;

import barley.appmgt.api.APIProvider;
import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.FaultGatewaysException;
import barley.appmgt.api.model.APIIdentifier;
import barley.appmgt.api.model.APIStatus;
import barley.appmgt.api.model.WebApp;
import barley.appmgt.impl.APIManagerFactory;
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.core.context.BarleyContext;
import barley.core.context.PrivilegedBarleyContext;
import barley.core.utils.BarleyUtils;
import barley.governance.api.generic.GenericArtifactManager;
import barley.governance.api.generic.dataobjects.GenericArtifact;
import barley.governance.api.util.GovernanceUtils;
import barley.governance.registry.extensions.interfaces.Execution;
import barley.registry.core.Registry;
import barley.registry.core.Resource;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.internal.RegistryCoreServiceComponent;
import barley.registry.core.jdbc.handlers.RequestContext;
import barley.registry.core.session.CurrentSession;
import barley.registry.core.session.UserRegistry;
import barley.user.api.UserRealm;

/**
 * Base executor for AppM lifecycle transition events.
 * (수정) 
 * Execution 구현으로 추상클래스를 제거함.  
 */
//public class __AppMGenericExecutor extends GenericExecutor {
public class AppMGenericExecutor implements Execution {

    private static final Log log= LogFactory.getLog(AppMGenericExecutor.class);
    private static final String REGISTRY_LC_NAME =  "registry.LC.name";

    private UserRealm userRealm;
    private int tenantId;
    //private StateExecutor stateExecutor;
    
    public void init(Map map) {
        obtainTenantId();
        obtainUserRealm();
        //this.stateExecutor=new StateExecutor(map);
    }

    public boolean execute(RequestContext requestContext, String fromState, String toState) {
    	Registry registry = null;
        String providerName = null;
        String assetType = null;
        String resourceID = null;

        try {
        	resourceID = requestContext.getResource().getUUID();
            // (수정)
            //registry = RegistryCoreServiceComponent.getRegistryService().getGovernanceUserRegistry(CurrentSession.getUser(), CurrentSession.getTenantId());
            registry = ServiceReferenceHolder.getInstance().
                    getRegistryService().getGovernanceUserRegistry(CurrentSession.getUser(), CurrentSession.getTenantId());
            
            String lifecycleName = requestContext.getResource().getProperty(REGISTRY_LC_NAME);
            //Load Gov Artifacts
            GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);
            if(AppMConstants.WEBAPP_LIFE_CYCLE.equals(lifecycleName)){
                assetType = AppMConstants.WEBAPP_ASSET_TYPE;
            }else if(AppMConstants.MOBILE_LIFE_CYCLE.equals(lifecycleName)){
                assetType = AppMConstants.MOBILE_ASSET_TYPE;
            }
            GenericArtifactManager artifactManager = new GenericArtifactManager(registry, assetType);

            // (추가) 2019.06.11
            GenericArtifact webAppArtifact = artifactManager.getGenericArtifact(resourceID);
            providerName = webAppArtifact.getAttribute(AppMConstants.API_OVERVIEW_PROVIDER);
            String appName = webAppArtifact.getAttribute(AppMConstants.API_OVERVIEW_NAME);
            String appVersion = webAppArtifact.getAttribute(AppMConstants.API_OVERVIEW_VERSION);
            
            WebApp app = AppManagerUtil.getAPI(webAppArtifact, registry);            
            APIProvider apiProvider = APIManagerFactory.getInstance().getAPIProvider(AppManagerUtil.replaceEmailDomainBack(providerName));
            APIStatus status = AppManagerUtil.getApiStatus(toState);
            apiProvider.changeAPIStatus(app, status, providerName, true);
            
            // (추가) 2019.06.11 - requestContext에 변경된 resource를 담지 않으면 저장되지 않는다. 따라서 로직을 추가하여 반영할 리소스를 context에 추가한다.
            APIIdentifier apiIdentifier = new APIIdentifier(providerName, appName, appVersion);
	        String appPath = AppManagerUtil.getAPIPath(apiIdentifier);
	        Resource appResource = registry.get(appPath);
	        requestContext.setResource(appResource);
	        
        } catch (RegistryException e) {
           log.error("Error occurred while obtaining provider details for webapp "+ resourceID);
        } catch (AppManagementException e) {
        	log.error("Error occurred while obtaining apiProvider");
		} catch (FaultGatewaysException e) {
			log.error("Error occurred while changing api status");
		}
        /*
        JaggeryThreadContext jaggeryThreadContext=new JaggeryThreadContext();

        //The path of the asset
        String path=requestContext.getResource().getPath();

        //Used to inject asset specific information to a permission instruction

        DynamicValueInjector dynamicValueInjector=new DynamicValueInjector();


        dynamicValueInjector.setDynamicValue(DynamicValueInjector.ASSET_AUTHOR_KEY, cleanUsername(providerName));

        //Execute all permissions for the current state
        //this.stateExecutor.executePermissions(this.userRealm,dynamicValueInjector,path,s2);
        
        jaggeryThreadContext.setFromState(fromState);
        jaggeryThreadContext.setToState(toState);
        jaggeryThreadContext.setAssetPath(path);
        jaggeryThreadContext.setDynamicValueInjector(dynamicValueInjector);
        //jaggeryThreadContext.setUserRealm(userRealm);
        jaggeryThreadContext.setStateExecutor(stateExecutor);
        JaggeryThreadLocalMediator.set(jaggeryThreadContext);*/

        return true;
    }
    
    private String cleanUsername(String provider){

        boolean isEmailEnabled =
                Boolean.parseBoolean(BarleyUtils.getServerConfiguration().getFirstProperty("EnableEmailUserName"));
        if (provider != null && !isEmailEnabled && provider.contains("-AT-")) {
            provider = provider.substring(0, provider.indexOf("-AT-"));
        }
        return provider;
    }

    /*
    The method obtains the tenant id from a string tenant id
     */
    private void obtainTenantId(){
        this.tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId();
    }

    /*
    The method is used to obtain the User Realm from the RealmContext
     */
    private void obtainUserRealm(){
        this.userRealm = PrivilegedBarleyContext.getThreadLocalCarbonContext().getUserRealm();
    }

}
