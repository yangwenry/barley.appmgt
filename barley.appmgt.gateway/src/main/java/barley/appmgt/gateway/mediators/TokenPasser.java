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

package barley.appmgt.gateway.mediators;

import java.util.Map;

import org.apache.synapse.MessageContext;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;

import barley.appmgt.gateway.handlers.security.APISecurityUtils;
import barley.appmgt.gateway.handlers.security.AuthenticationContext;

/**
 *  Mediator class used to add custom header containing WebApp subscriber's name (caller)
 *  to request message being forwarded to actual endpoint.
 */
public class TokenPasser extends AbstractMediator {

    public boolean mediate(MessageContext synCtx) {
        AuthenticationContext authContext = APISecurityUtils.getAuthenticationContext(synCtx);
        addHTTPHeader(synCtx,authContext);
        return true;
    }

    private void addHTTPHeader(MessageContext synCtx, AuthenticationContext authContext) {
        Map transportHeaders = (Map)((Axis2MessageContext) synCtx).getAxis2MessageContext()
                .getProperty(org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
        //transportHeaders.put("assertion", authContext.getCallerToken());
    }
    
    public boolean isContentAware(){
        return false;
    }
}
