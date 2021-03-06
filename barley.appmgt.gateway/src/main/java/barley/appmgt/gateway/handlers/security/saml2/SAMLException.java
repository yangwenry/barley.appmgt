/*
 *
 *  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *   in compliance with the License.
 *   you may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing,
 *   software distributed under the License is distributed on an
 *   "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *   KIND, either express or implied.  See the License for the
 *   specific language governing permissions and limitations
 *   under the License.
 *
 */

package barley.appmgt.gateway.handlers.security.saml2;

/**
 * This exception will be thrown when something goes wrong with SAML request/response processing.
 */
public class SAMLException extends Exception {


    public SAMLException() {
        super();
    }

    public SAMLException(String message) {
        super(message);
    }

    public SAMLException(String message, Throwable cause) {
        super(message, cause);
    }
}
