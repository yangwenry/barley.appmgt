/*
 * Copyright (c) 2005-2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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

package barley.appmgt.impl.workflow;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.dao.AppMDAO;
import barley.appmgt.impl.dto.ApplicationWorkflowDTO;
import barley.appmgt.impl.dto.WorkflowDTO;

/**
 * This is a simple work flow extension to have Application creation process
 * 
 */
public class ApplicationCreationSimpleWorkflowExecutor extends WorkflowExecutor {

	private static final Log log =
	                               LogFactory.getLog(ApplicationCreationSimpleWorkflowExecutor.class);

	@Override
	public String getWorkflowType() {
		return WorkflowConstants.WF_TYPE_AM_APPLICATION_CREATION;
	}

	/**
	 * Execute the workflow executor
	 * 
	 * @param workFlowDTO
	 *            - {@link ApplicationWorkflowDTO}
	 * @throws WorkflowException
	 */

	public void execute(WorkflowDTO workFlowDTO) throws WorkflowException {
		if (log.isDebugEnabled()) {
			log.info("Executing Application creation Workflow..");
		}
		workFlowDTO.setStatus(WorkflowStatus.APPROVED);
		complete(workFlowDTO);

	}

	/**
	 * Complete the external process status
	 * Based on the workflow status we will update the status column of the
	 * Application table
	 * 
	 * @param workFlowDTO - WorkflowDTO
	 */
	public void complete(WorkflowDTO workFlowDTO) throws WorkflowException {
		if (log.isDebugEnabled()) {
			log.info("Complete  Application creation Workflow..");
		}
		
		String status = null;
		if ("CREATED".equals(workFlowDTO.getStatus().toString())) {
			status = AppMConstants.ApplicationStatus.APPLICATION_CREATED;
		} else if ("REJECTED".equals(workFlowDTO.getStatus().toString())) {
			status = AppMConstants.ApplicationStatus.APPLICATION_REJECTED;
		} else if ("APPROVED".equals(workFlowDTO.getStatus().toString())) {
			status = AppMConstants.ApplicationStatus.APPLICATION_APPROVED;
		}

		AppMDAO dao = new AppMDAO();
		
		try {
			dao.updateApplicationStatus(Integer.parseInt(workFlowDTO.getWorkflowReference()),status);
		} catch (AppManagementException e) {
			String msg = "Error occured when updating the status of the Application creation process";
			log.error(msg, e);
			throw new WorkflowException(msg, e);
		}
	}

	@Override
	public List<WorkflowDTO> getWorkflowDetails(String workflowStatus) throws WorkflowException {
		return null;
	}

}
