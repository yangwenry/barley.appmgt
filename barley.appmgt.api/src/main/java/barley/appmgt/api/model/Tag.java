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
 * Represents a tag(s) used to categorize an WebApp.
 *
 * @see WebApp
 */
@SuppressWarnings("unused")
public class Tag {

    private String name;
    private int noOfOccurrences;

    public Tag(String name) {
        this.name = name;
    }

    public Tag(String name, int noOfOccurrences) {
        this.name = name;
        this.noOfOccurrences = noOfOccurrences;
    }

    public String getName() {
        return name;
    }

    public int getNoOfOccurrences() {
        return noOfOccurrences;
    }

    public void setNoOfOccurrences(int noOfOccurrences) {
        this.noOfOccurrences = noOfOccurrences;
    }
}
