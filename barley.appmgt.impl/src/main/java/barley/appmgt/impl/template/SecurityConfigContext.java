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

package barley.appmgt.impl.template;

import org.apache.velocity.VelocityContext;

import barley.appmgt.api.model.WebApp;
import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.service.ServiceReferenceHolder;
/**
 * Set the parameters for secured endpoints
 */
public class SecurityConfigContext extends ConfigContextDecorator {

    private WebApp api;

    public SecurityConfigContext(ConfigContext context,WebApp api) {
        super(context);
        this.api = api;
    }

    public VelocityContext getContext() {
        VelocityContext context = super.getContext();

        String alias =  api.getId().getProviderName() + "--" + api.getId().getApiName()
                        + api.getId().getVersion();

        boolean isSecureVaultEnabled = Boolean.parseBoolean(ServiceReferenceHolder.getInstance().getAPIManagerConfigurationService().getAPIManagerConfiguration().getFirstProperty(AppMConstants.API_SECUREVAULT_ENABLE));
        
        context.put("isEndpointSecured", api.isEndpointSecured());
        context.put("username", api.getEndpointUTUsername());
        context.put("securevault_alias", alias);
        context.put("password", api.getEndpointUTPassword());
        context.put("isSecureVaultEnabled", isSecureVaultEnabled);
        
        return context;
    }

}
