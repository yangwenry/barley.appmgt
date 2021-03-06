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

import barley.appmgt.api.model.WebApp;

/**
 * This is written to make sure backward compatibility of the APIs created prior AM 1.6.0v
 */
public class EndpointBckConfigContext extends ConfigContextDecorator {

    private WebApp api;

    public EndpointBckConfigContext(ConfigContext context, WebApp api) {
        super(context);
        this.api = api;
        //check if endpoint_config not set
        String endpoint_config = api.getEndpointConfig();
        if(endpoint_config == null || "".equals(endpoint_config.trim())){
            // Without setting the context make the endpoint_config json of api
            // The following config will be picked up by EndpointConfigContext
            endpoint_config = "{\"production_endpoints\":{\"url\":\""+ api.getUrl()+"\", \"config\":null},\"sandbox_endpoints\":{\"url\":\""+api.getSandboxUrl()+"\",\"config\":null},\"endpoint_type\":\"http\"}";
            api.setEndpointConfig(endpoint_config);
        }
    }
}
