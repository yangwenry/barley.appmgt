/*
*  Copyright (c) 2005-2008, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*
*/

package barley.appmgt.impl.observers;

import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.core.context.PrivilegedBarleyContext;
import barley.core.utils.AbstractAxis2ConfigurationContextObserver;

/**
 * This creates the subscriber Role for a tenant, when a new tenant is created.
 */
public class SignupObserver extends AbstractAxis2ConfigurationContextObserver {

    private static final Log log = LogFactory.getLog(SignupObserver.class);

    public void createdConfigurationContext(ConfigurationContext configurationContext) {
        int tenantId = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantId();
        String tenantDomain = PrivilegedBarleyContext.getThreadLocalCarbonContext().getTenantDomain();
        try {
            AppManagerUtil.loadTenantSelfSignUpConfigurations(tenantId);
            AppManagerUtil.createSelfSignUpRoles(tenantId);

        } catch (AppManagementException e) {
            log.error("Error while adding subscriber roles for tenant : " + tenantDomain, e);
        }
    }
}
