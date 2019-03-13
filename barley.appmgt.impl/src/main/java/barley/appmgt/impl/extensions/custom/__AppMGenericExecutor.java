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
import org.wso2.carbon.registry.core.jdbc.handlers.RequestContext;
import org.wso2.carbon.utils.CarbonUtils;
import org.wso2.jaggery.scxml.generic.GenericExecutor;
import org.wso2.jaggery.scxml.management.DynamicValueInjector;
import org.wso2.jaggery.scxml.management.StateExecutor;
import org.wso2.jaggery.scxml.threading.JaggeryThreadLocalMediator;
import org.wso2.jaggery.scxml.threading.contexts.JaggeryThreadContext;

import barley.appmgt.impl.AppMConstants;
import barley.core.context.PrivilegedBarleyContext;
import barley.governance.api.generic.GenericArtifactManager;
import barley.governance.api.generic.dataobjects.GenericArtifact;
import barley.governance.api.util.GovernanceUtils;
import barley.registry.core.Registry;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.internal.RegistryCoreServiceComponent;
import barley.registry.core.session.CurrentSession;
import barley.registry.core.session.UserRegistry;
import barley.user.api.UserRealm;

/**
 * Base executor for AppM lifecycle transition events.
 *
 */
public class __AppMGenericExecutor extends GenericExecutor {

    private static final Log log= LogFactory.getLog(ApproveEventExecutor.class);
    private static final String REGISTRY_LC_NAME =  "registry.LC.name";

    private UserRealm userRealm;
    private int tenantId;
    private StateExecutor stateExecutor;

    @Override
    public void init(Map map) {
        obtainTenantId();
        obtainUserRealm();
        this.stateExecutor=new StateExecutor(map);
    }

    @Override
    public boolean execute(RequestContext requestContext, String fromState, String toState) {
    	/* (임시주석) RequestContext 파라미터 타입을 변경해야 함. 
        Registry registry = null;
        String providerName = null;
        String assetType = null;
        String resourceID = null;

        try {
            resourceID = requestContext.getResource().getUUID();
            registry = RegistryCoreServiceComponent.getRegistryService().getGovernanceUserRegistry(CurrentSession.getUser(), CurrentSession.getTenantId());
            String lifecycleName = requestContext.getResource().getProperty(REGISTRY_LC_NAME);
            //Load Gov Artifacts
            GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);
            if(AppMConstants.WEBAPP_LIFE_CYCLE.equals(lifecycleName)){
                assetType = AppMConstants.WEBAPP_ASSET_TYPE;
            }else if(AppMConstants.MOBILE_LIFE_CYCLE.equals(lifecycleName)){
                assetType = AppMConstants.MOBILE_ASSET_TYPE;
            }
            GenericArtifactManager artifactManager = new GenericArtifactManager(registry, assetType);

            GenericArtifact webAppArtifact = artifactManager.getGenericArtifact(resourceID);
            providerName = webAppArtifact.getAttribute(AppMConstants.API_OVERVIEW_PROVIDER);
        } catch (RegistryException e) {
           log.error("Error occurred while obtaining provider details for webapp "+ resourceID);
        }


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
        jaggeryThreadContext.setUserRealm(userRealm);
        jaggeryThreadContext.setStateExecutor(stateExecutor);
        JaggeryThreadLocalMediator.set(jaggeryThreadContext);

        return true;
        */
    	return true;
    }

    private String cleanUsername(String provider){

        boolean isEmailEnabled =
                Boolean.parseBoolean(CarbonUtils.getServerConfiguration().getFirstProperty("EnableEmailUserName"));
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
