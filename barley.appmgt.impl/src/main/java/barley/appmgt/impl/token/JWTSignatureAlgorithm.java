/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package barley.appmgt.impl.token;

//To be fixed with - https://wso2.org/jira/browse/APPM-1060

/**
 *  This class represents the JWT signature algorithms.
 */
public enum JWTSignatureAlgorithm {

    SHA256_WITH_RSA("RS256"), NONE("none");

    private String jwsCompliantCode;

    private JWTSignatureAlgorithm(String s){
        jwsCompliantCode = s;
    }

    public String getJwsCompliantCode() {
        return jwsCompliantCode;
    }
}
