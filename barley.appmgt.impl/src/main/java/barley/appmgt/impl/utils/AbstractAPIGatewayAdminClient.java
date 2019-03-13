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

package barley.appmgt.impl.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.rmi.RemoteException;

import org.apache.axis2.AxisFault;
import org.apache.axis2.client.Options;
import org.apache.axis2.client.ServiceClient;
import org.apache.axis2.client.Stub;
import org.apache.axis2.context.ServiceContext;
import org.apache.axis2.transport.http.HTTPConstants;
import org.wso2.carbon.authenticator.stub.AuthenticationAdminStub;
import org.wso2.carbon.authenticator.stub.LoginAuthenticationExceptionException;

import barley.appmgt.impl.dto.Environment;

/**
 * An abstract service client implementation that can be used to access any admin service
 * hosted in the WebApp gateway. This implementation loads the necessary admin service
 * credentials and the WebApp gateway connection settings from the AppManagerConfiguration.
 */
public abstract class AbstractAPIGatewayAdminClient {

    /**
     * Log into the WebApp gateway as an admin, and initialize the specified client stub using
     * the established authentication session. This method will also set some timeout
     * values and enable session management on the stub so that it can be successfully used
     * for any subsequent admin service invocations.
     * 
     * @param stub A client stub to be setup
     * @throws AxisFault if an error occurs when logging into the WebApp gateway
     */
    protected void setup(Stub stub, Environment environment) throws AxisFault {
        String cookie = login(environment);
        ServiceClient client = stub._getServiceClient();
        Options options = client.getOptions();
        options.setTimeOutInMilliSeconds(15 * 60 * 1000);
        options.setProperty(HTTPConstants.SO_TIMEOUT, 15 * 60 * 1000);
        options.setProperty(HTTPConstants.CONNECTION_TIMEOUT, 15 * 60 * 1000);
        options.setManageSession(true);
        options.setProperty(org.apache.axis2.transport.http.HTTPConstants.COOKIE_STRING, cookie);
    }

    /**
     * Login to the WebApp gateway as an admin
     * 
     * @return A session cookie string
     * @throws AxisFault if an error occurs while logging in
     */
    private String login(Environment environment) throws AxisFault {
        String user = environment.getUserName();
        String password = environment.getPassword();
        String serverURL = environment.getServerURL();
        
        if (serverURL == null || user == null || password == null) {
            throw new AxisFault("Required WebApp gateway admin configuration unspecified");
        }

        String host;
        try {
            host = new URL(serverURL).getHost();
        } catch (MalformedURLException e) {
            throw new AxisFault("WebApp gateway URL is malformed", e);
        }

        AuthenticationAdminStub authAdminStub = new AuthenticationAdminStub(null, serverURL + "AuthenticationAdmin");
        ServiceClient client = authAdminStub._getServiceClient();
        Options options = client.getOptions();
        options.setManageSession(true);
        try {
            authAdminStub.login(user, password, host);
            ServiceContext serviceContext = authAdminStub.
                    _getServiceClient().getLastOperationContext().getServiceContext();
            String sessionCookie = (String) serviceContext.getProperty(HTTPConstants.COOKIE_STRING);
            return sessionCookie;
        } catch (RemoteException e) {
            throw new AxisFault("Error while contacting the authentication admin services", e);
        } catch (LoginAuthenticationExceptionException e) {
            throw new AxisFault("Error while authenticating against the WebApp gateway admin", e);
        }
    }

}
