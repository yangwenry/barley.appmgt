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

package barley.appmgt.impl.workflow;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.AppManagerConfiguration;
import barley.appmgt.impl.dto.UserRegistrationConfigDTO;
import barley.appmgt.impl.dto.WorkflowDTO;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import barley.appmgt.impl.utils.SelfSignUpUtil;
import barley.core.multitenancy.MultitenantUtils;

public class UserSignUpSimpleWorkflowExecutor extends UserSignUpWorkflowExecutor {

    private static final Log log = LogFactory.getLog(UserSignUpSimpleWorkflowExecutor.class);

    @Override
    public String getWorkflowType() {
        return WorkflowConstants.WF_TYPE_AM_USER_SIGNUP;
    }

    @Override
    public void execute(WorkflowDTO workflowDTO) throws WorkflowException {
        if (log.isDebugEnabled()) {
            log.debug("Executing User SignUp Workflow for " +
                    workflowDTO.getWorkflowReference());
        }
        complete(workflowDTO);
        // super.publishEvents(workflowDTO);
    }

    @Override
    public void complete(WorkflowDTO workflowDTO) throws WorkflowException {
        if (log.isDebugEnabled()) {
            log.debug("User Sign Up [Complete] Workflow Invoked. Workflow ID : " +
                    workflowDTO.getExternalWorkflowReference() + "Workflow State : " +
                    workflowDTO.getStatus());
        }
        AppManagerConfiguration config = ServiceReferenceHolder.getInstance()
                .getAPIManagerConfigurationService()
                .getAPIManagerConfiguration();

        String serverURL = config.getFirstProperty(AppMConstants.AUTH_MANAGER_URL);

        String tenantDomain = workflowDTO.getTenantDomain();

        try {
           int tenantId = ServiceReferenceHolder.getInstance().getRealmService().getTenantManager()
                    .getTenantId(tenantDomain);

            UserRegistrationConfigDTO signupConfig =
                    SelfSignUpUtil.getSignupConfiguration(tenantId);

            if (serverURL == null || signupConfig.getAdminUserName() == null ||
                    signupConfig.getAdminPassword() == null) {
                throw new WorkflowException("Required parameter missing to connect to the"
                        + " authentication manager");
            }

            String tenantAwareUserName =
                    MultitenantUtils.getTenantAwareUsername(workflowDTO.getWorkflowReference());
            updateRolesOfUser(serverURL, signupConfig.getAdminUserName(),
                    signupConfig.getAdminPassword(), tenantAwareUserName,
                    SelfSignUpUtil.getRoleNames(signupConfig), tenantDomain);
        } catch (AppManagementException e) {
            throw new WorkflowException("Error while accessing signup configuration", e);
        } catch (Exception e) {
            throw new WorkflowException("Error while assigning role to user", e);
        }
    }


    @Override
    public List<WorkflowDTO> getWorkflowDetails(String workflowStatus) throws WorkflowException{
        return null;
    }

}
