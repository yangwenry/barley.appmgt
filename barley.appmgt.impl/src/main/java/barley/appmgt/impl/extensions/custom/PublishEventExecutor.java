/**
 *  Copyright (c) WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package barley.appmgt.impl.extensions.custom;

import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.jaggery.scxml.management.DynamicValueInjector;
import org.wso2.jaggery.scxml.management.StateExecutor;
import org.wso2.jaggery.scxml.threading.JaggeryThreadLocalMediator;
import org.wso2.jaggery.scxml.threading.contexts.JaggeryThreadContext;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.APIIdentifier;
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.dao.AppMDAO;
import barley.appmgt.impl.dto.PublishApplicationWorkflowDTO;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.appmgt.impl.workflow.WorkflowConstants;
import barley.appmgt.impl.workflow.WorkflowException;
import barley.appmgt.impl.workflow.WorkflowExecutor;
import barley.appmgt.impl.workflow.WorkflowExecutorFactory;
import barley.appmgt.impl.workflow.WorkflowStatus;
import barley.core.context.BarleyContext;
import barley.core.context.PrivilegedBarleyContext;
import barley.core.utils.BarleyUtils;
import barley.governance.api.generic.GenericArtifactManager;
import barley.governance.api.generic.dataobjects.GenericArtifact;
import barley.governance.api.util.GovernanceUtils;
import barley.governance.registry.extensions.interfaces.Execution;
import barley.registry.core.Registry;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.internal.RegistryCoreServiceComponent;
import barley.registry.core.jdbc.handlers.RequestContext;
import barley.registry.core.session.CurrentSession;
import barley.registry.core.session.UserRegistry;
import barley.user.api.UserRealm;

/*
Description:The executor parses the parameter map defined in the
            registry.xml transition element and creates a JaggeryThreadContext
            which contains information on the permissions to be changed.
            The actual permission change logic runs in the JaggeryExecutorHandler.
Filename: GenericExecutor.java
Created Date: 26/8/2013
 */
public class PublishEventExecutor implements Execution
{
    private static final Log log=LogFactory.getLog(PublishEventExecutor.class);

    private UserRealm userRealm;
    private int tenantId;
    private StateExecutor stateExecutor;

    public void init(Map map) {

        obtainTenantId();
        obtainUserRealm();
        this.stateExecutor=new StateExecutor(map);
    }


    /*
    The method performs some logic when ever a state transition takes place
    @requestContext: Contains context data about the transition
    e.g. Registry and Resource
    s: From state
    s2: To state
    @return: True if the execution took place correctly
     */
    public boolean execute(RequestContext requestContext, String s, String s2){
        String resourceID = requestContext.getResource().getUUID();
        String appName = null;
        String appVersion = null;
        String appProvider = null;
        String lcState = null;
        String tenantDomain = null;
        String workflowRef= null;
        String newState = null;

        AppMDAO appMDAO;
        APIIdentifier apiIdentifier;

        try{
            //Get the registry
        	// (수정) 2018.03.13 - APPServiceHolder로 변경 
//            Registry registry = RegistryCoreServiceComponent.getRegistryService().getGovernanceUserRegistry(CurrentSession.getUser(), CurrentSession.getTenantId());
        	Registry registry = ServiceReferenceHolder.getInstance().getRegistryService().getGovernanceUserRegistry(CurrentSession.getUser(), CurrentSession.getTenantId());
            
            //Load Gov Artifacts
            GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);

            GenericArtifactManager artifactManager = new GenericArtifactManager(registry, AppMConstants.API_KEY);

            GenericArtifact webappArtifact = artifactManager.getGenericArtifact(resourceID);

            appName = webappArtifact.getAttribute("overview_name");
            appVersion = webappArtifact.getAttribute("overview_version");
            tenantDomain = BarleyContext.getThreadLocalCarbonContext().getTenantDomain();
            tenantId = CurrentSession.getTenantId();

            appProvider = webappArtifact.getAttribute("overview_provider");
            workflowRef = concatStrings(appName,appVersion,appProvider,tenantDomain);

            newState = s2;

            lcState = webappArtifact.getLifecycleState();

        }catch (RegistryException e){
            //Change the interface impl to thorw exception
            log.error("Error while trying to retrieve registry artifact.", e);
            return false;
        }

        WorkflowExecutor appPublishWFExecutor = null;
        try {
            appPublishWFExecutor = WorkflowExecutorFactory.getInstance().
                    getWorkflowExecutor(WorkflowConstants.WF_TYPE_AM_APP_PUBLISH);
        } catch (WorkflowException e) {
            log.error("Error while executing workflow.", e);
            return false;
        }
        
        PublishApplicationWorkflowDTO workflowDTO = new PublishApplicationWorkflowDTO();
        //This is the status of the workflow, and not the APP
        workflowDTO.setStatus(WorkflowStatus.CREATED);
        workflowDTO.setCreatedTime(System.currentTimeMillis());
        workflowDTO.setExternalWorkflowReference(appPublishWFExecutor.generateUUID());
        workflowDTO.setWorkflowReference(String.valueOf(workflowRef));
        workflowDTO.setWorkflowType(WorkflowConstants.WF_TYPE_AM_APP_PUBLISH);
        workflowDTO.setCallbackUrl(appPublishWFExecutor.getCallbackURL());
        workflowDTO.setAppName(appName);
        workflowDTO.setLcState(lcState);
        workflowDTO.setNewState(newState);
        workflowDTO.setAppVersion(appVersion);
        workflowDTO.setAppProvider(appProvider);
        workflowDTO.setTenantId(tenantId);
        workflowDTO.setTenantDomain(tenantDomain);

        try {
            appPublishWFExecutor.execute(workflowDTO);
        } catch (WorkflowException e) {
            log.error("Could not execute Application Publish Workflow", e);
            //throw new AppManagementException("Could not execute Application Publish Workflow", e);
            return false;
        }


        if(s2.equalsIgnoreCase(AppMConstants.ApplicationStatus.APPLICATION_RETIRED)) {
            appMDAO = new AppMDAO();
            apiIdentifier = new APIIdentifier(appProvider, appName, appVersion);

            try {
                appMDAO.removeAPISubscription(apiIdentifier);
            } catch (AppManagementException e) {
                log.error("Could not remove subscription when Unpublishing", e);
                return false;
            }
        }

        JaggeryThreadContext jaggeryThreadContext=new JaggeryThreadContext();

        //The path of the asset
        String path=requestContext.getResource().getPath();

        //Used to inject asset specific information to a permission instruction

        DynamicValueInjector dynamicValueInjector=new DynamicValueInjector();

        boolean isEmailEnabled = Boolean.parseBoolean(BarleyUtils.getServerConfiguration().getFirstProperty("EnableEmailUserName"));
        String provider = requestContext.getResource().getAuthorUserName();
//        TODO: Check email enabled case and remove or uncomment the following
//        if (provider != null && !isEmailEnabled && provider.contains("-AT-")) {
//            provider = provider.substring(0, provider.indexOf("-AT-"));
//
//        }

        //Set the asset author key
        dynamicValueInjector.setDynamicValue(DynamicValueInjector.ASSET_AUTHOR_KEY, provider);

        //Execute all permissions for the current state
        //this.stateExecutor.executePermissions(this.userRealm,dynamicValueInjector,path,s2);

        jaggeryThreadContext.setFromState(s);
        jaggeryThreadContext.setToState(s2);
        jaggeryThreadContext.setAssetPath(path);
        jaggeryThreadContext.setDynamicValueInjector(dynamicValueInjector);
        // (임시주석)
        //jaggeryThreadContext.setUserRealm(userRealm);
        jaggeryThreadContext.setStateExecutor(stateExecutor);

        JaggeryThreadLocalMediator.set(jaggeryThreadContext);

        return true;
    }

    private String concatStrings(String appName, String appVersion, String appProvider, String tenantDomain){
        StringBuilder sb = new StringBuilder();
        sb.append(appName.concat(":"));
        sb.append(appVersion.concat(":"));
        //replace ':' with '/'
        String provider = AppManagerUtil.makeSecondaryUSNameDBFriendly(appProvider);
        sb.append(provider.concat(":"));
        sb.append(tenantDomain);
        return sb.toString();
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
