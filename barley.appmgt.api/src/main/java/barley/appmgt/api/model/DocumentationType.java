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

package barley.appmgt.api.model;

/**
 * Types of Documentation that can exist for an WebApp.
 */
@SuppressWarnings("unused")
public enum DocumentationType {
    HOWTO("How To"),
    SAMPLES("Samples"),
    PUBLIC_FORUM("Public Forum"),
    SUPPORT_FORUM("Support Forum"),
    API_MESSAGE_FORMAT("WebApp Message Format"),
    SWAGGER_DOC("Swagger WebApp Definition"),
    OTHER("Other");

    private String type;

    private DocumentationType(String type) {
        this.type = type;
    }

    public String getType() {
        return type;
    }
}
