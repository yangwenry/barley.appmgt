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
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.dao.AppMDAO;
import barley.appmgt.impl.dto.PublishApplicationWorkflowDTO;
import barley.appmgt.impl.dto.WorkflowDTO;
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
public class ApproveEventExecutor implements Execution
{
    private static final Log log=LogFactory.getLog(ApproveEventExecutor.class);

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
    public boolean execute(RequestContext requestContext, String s, String s2) {
        WorkflowExecutor appPublishWFExecutor = null;
        try {
            appPublishWFExecutor = WorkflowExecutorFactory.getInstance().
                    getWorkflowExecutor(WorkflowConstants.WF_TYPE_AM_APP_PUBLISH);
        } catch (WorkflowException e) {
            log.error("Error while initiating workflow",e);
            return false;
        }

        if(appPublishWFExecutor.isAsynchronus() || WorkflowStatus.PUBLISHED.name().equalsIgnoreCase(s2)){
            String resourceID = requestContext.getResource().getUUID();
            String appName = null;
            String appVersion = null;
            String appProvider = null;
            String tenantDomain = null;

            try{
                //Get the registry
            	// (수정)
//                Registry registry = RegistryCoreServiceComponent.getRegistryService().getGovernanceUserRegistry(CurrentSession.getUser(), CurrentSession.getTenantId());
            	Registry registry = ServiceReferenceHolder.getInstance().
                        getRegistryService().getGovernanceUserRegistry(CurrentSession.getUser(), CurrentSession.getTenantId());
            	
                //Load Gov Artifacts
                GovernanceUtils.loadGovernanceArtifacts((UserRegistry) registry);

                GenericArtifactManager artifactManager = new GenericArtifactManager(registry, AppMConstants.API_KEY);

                GenericArtifact webappArtifact = artifactManager.getGenericArtifact(resourceID);

                appName = webappArtifact.getAttribute("overview_name");
                appVersion = webappArtifact.getAttribute("overview_version");
                appProvider = webappArtifact.getAttribute("overview_provider");
                tenantDomain = BarleyContext.getThreadLocalCarbonContext().getTenantDomain();

                String searchString = concatStrings(appName,appVersion,appProvider,tenantDomain);

                AppMDAO dao = new AppMDAO();

                try{
                    WorkflowDTO workflowDTO = dao.retrieveLatestWorkflowByReference(searchString);
                    try {
                        if(workflowDTO!=null){
                            PublishApplicationWorkflowDTO publishhAppDTO = new PublishApplicationWorkflowDTO();
                            if (WorkflowStatus.PUBLISHED.name().equalsIgnoreCase(s2)) {
                                publishhAppDTO.setStatus(WorkflowStatus.PUBLISHED);
                            } else {
                                publishhAppDTO.setStatus(WorkflowStatus.APPROVED);
                            }
                            publishhAppDTO.setExternalWorkflowReference(workflowDTO.getExternalWorkflowReference());
                            publishhAppDTO.setWorkflowReference(workflowDTO.getWorkflowReference());
                            publishhAppDTO.setWorkflowType(workflowDTO.getWorkflowType());
                            publishhAppDTO.setCallbackUrl(workflowDTO.getCallbackUrl());
                            publishhAppDTO.setAppName(appName);
                            publishhAppDTO.setLcState(s);
                            publishhAppDTO.setNewState(s2);
                            publishhAppDTO.setAppVersion(appVersion);
                            publishhAppDTO.setAppProvider(appProvider);
                            publishhAppDTO.setTenantId(tenantId);
                            publishhAppDTO.setTenantDomain(tenantDomain);

                            appPublishWFExecutor.complete(publishhAppDTO);
                        }else{
                            throw new WorkflowException("Workflow Reference not found");
                        }

                    } catch (WorkflowException e) {
                        log.error("Could not execute Application Publish Workflow", e);
                        return false;
                    }

                }catch(AppManagementException e){
                    log.error("Error while retrieving workflow details from database");
                    return false;
                }
            }catch (RegistryException e){
                log.error("Error while loading artifact details from registry");
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
//            if (provider != null && !isEmailEnabled && provider.contains("-AT-")) {
//                provider = provider.substring(0, provider.indexOf("-AT-"));
//
//            }

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
