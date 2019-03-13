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
import barley.appmgt.impl.dao.AppMDAO;
import barley.appmgt.impl.dto.WorkflowDTO;

public class SubscriptionCreationSimpleWorkflowExecutor extends WorkflowExecutor {

    private static final Log log = LogFactory.getLog(SubscriptionCreationSimpleWorkflowExecutor.class);

    @Override
    public String getWorkflowType() {
        return WorkflowConstants.WF_TYPE_AM_SUBSCRIPTION_CREATION;
    }

    @Override
    public List<WorkflowDTO> getWorkflowDetails(String workflowStatus) throws WorkflowException{
        return null;
    }

    @Override
    public void execute(WorkflowDTO workflowDTO) throws WorkflowException{
        workflowDTO.setStatus(WorkflowStatus.APPROVED);
        complete(workflowDTO);
    }

    @Override
    public void complete(WorkflowDTO workflowDTO) throws WorkflowException{
        AppMDAO appMDAO = new AppMDAO();
        try {
            appMDAO.updateSubscriptionStatus(Integer.parseInt(workflowDTO.getWorkflowReference()),
                    AppMConstants.SubscriptionStatus.UNBLOCKED);
        } catch (AppManagementException e) {
            log.error("Could not complete subscription creation workflow", e);
            throw new WorkflowException("Could not complete subscription creation workflow", e);
        }
    }
}
