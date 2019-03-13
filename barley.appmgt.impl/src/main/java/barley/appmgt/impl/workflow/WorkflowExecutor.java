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

import java.io.Serializable;
import java.util.List;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.impl.dao.AppMDAO;
import barley.appmgt.impl.dto.WorkflowDTO;
import barley.registry.core.utils.UUIDGenerator;

/**
 * This is the class that should be extended by each workflow implementation.
 */
public abstract class WorkflowExecutor implements Serializable{

    //This variable indicates that the workflow is not dependent
    protected String callbackURL;

    public boolean isAsynchronus() {
        return true;
    }

    /**
     * Returns the workflow executor type. It is better to follow a convention as PRODUCT_ARTIFACT_ACTION for the
     * workflow type. Ex: AM_SUBSCRIPTION_CREATION.
     * @return - The workflow type.
     */
    public abstract String getWorkflowType();

    /**
     * Implements the workflow execution logic.
     * @param workflowDTO - The WorkflowDTO which contains workflow contextual information related to the workflow.
     * @throws WorkflowException - Thrown when the workflow execution was not fully performed.
     */
    public void execute(WorkflowDTO workflowDTO) throws WorkflowException{
        AppMDAO appMDAO = new AppMDAO();
        try {
            appMDAO.addWorkflowEntry(workflowDTO);
        } catch (AppManagementException e) {
            throw new WorkflowException("Error while persisting workflow", e);
        }
    }

    /**
     * Implements the workflow completion logic.
     * @param workflowDTO - The WorkflowDTO which contains workflow contextual information related to the workflow.
     * @throws WorkflowException - Thrown when the workflow completion was not fully performed.
     */
    public void complete(WorkflowDTO workflowDTO) throws WorkflowException{
        AppMDAO appMDAO = new AppMDAO();
        try {
            appMDAO.updateWorkflowStatus(workflowDTO);
        } catch (AppManagementException e) {
            throw new WorkflowException("Error while updating workflow", e);
        }
    }

    /**
     * Returns the information of the workflows whose status' match the workflowStatus
     * @param workflowStatus - The status of the workflows to match
     * @return - List of workflows whose status' matches the workflowStatus param. 'null' if no matches found.
     * @throws WorkflowException - Thrown when the workflow information could not be retrieved.
     */
    public abstract List<WorkflowDTO> getWorkflowDetails(String workflowStatus) throws WorkflowException;

    /**
     * Method generates and returns UUID
     * @return UUID
     */
    public String generateUUID(){
        String UUID = UUIDGenerator.generateUUID();
        return UUID;
    }

    /**
     * Method for persisting Workflow DTO
     * @param workflowDTO
     * @throws WorkflowException
     */
    public void persistWorkflow(WorkflowDTO workflowDTO) throws WorkflowException {
        AppMDAO appMDAO = new AppMDAO();
        try {
            appMDAO.addWorkflowEntry(workflowDTO);
        } catch (AppManagementException e) {
            throw new WorkflowException("Error while persisting workflow", e);
        }
    }

    public String getCallbackURL() {
        return callbackURL;
    }

    public void setCallbackURL(String callbackURL) {
        this.callbackURL = callbackURL;
    }

}
