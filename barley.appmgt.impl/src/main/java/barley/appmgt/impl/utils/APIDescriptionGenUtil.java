/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package barley.appmgt.impl.utils;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import javax.xml.namespace.QName;

import org.apache.axiom.om.OMElement;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.impl.AppMConstants;

public class APIDescriptionGenUtil {
    /**
     * Class Logger
     */
    private static Log log = LogFactory.getLog(APIDescriptionGenUtil.class);

    private static final String DESCRIPTION = "Allows [1] request(s) per minute.";

    public static String generateDescriptionFromPolicy(OMElement policy) throws
            AppManagementException {
        // Here as the method is about extracting some info from the policy. And it's not concern on compliance to
        // specification. So it just extract the required element.
        int requestPerMinute = generateMaxCountFromPolicy(policy);
        if (requestPerMinute >= 1) {
            String description = DESCRIPTION.replaceAll("\\[1\\]", Integer.toString(requestPerMinute));
            return description;
        }
        return DESCRIPTION;
    }

    public static int generateMaxCountFromPolicy(OMElement policy) throws
            AppManagementException {
        OMElement maxCount = null;
        OMElement timeUnit = null;
        int requestPerMinute;
        try {
            maxCount = policy.getFirstChildWithName(AppMConstants.POLICY_ELEMENT).getFirstChildWithName
                    (AppMConstants.THROTTLE_CONTROL_ELEMENT).getFirstChildWithName(AppMConstants.POLICY_ELEMENT).
                    getFirstChildWithName(AppMConstants.THROTTLE_MAXIMUM_COUNT_ELEMENT);
            timeUnit = policy.getFirstChildWithName(AppMConstants.POLICY_ELEMENT).getFirstChildWithName
                    (AppMConstants.THROTTLE_CONTROL_ELEMENT).getFirstChildWithName(AppMConstants.POLICY_ELEMENT).
                    getFirstChildWithName(AppMConstants.THROTTLE_UNIT_TIME_ELEMENT);
            //Here we will assume time unit provided as milli second and do calculation to get requests per minute.
            if (maxCount.getText().isEmpty() || timeUnit.getText().isEmpty()) {
                String msg = AppMConstants.THROTTLE_MAXIMUM_COUNT_ELEMENT.toString() + "or" +
                             AppMConstants.THROTTLE_UNIT_TIME_ELEMENT.toString() + " element data found empty in " +
                             "the policy.";
                log.warn(msg);
                throw new AppManagementException(msg);
            }
            requestPerMinute = (Integer.parseInt(maxCount.getText().trim()) * 60000) /
                               (Integer.parseInt(timeUnit.getText().trim()));
            return requestPerMinute;
        } catch (NullPointerException npe) {
            String msg = "Policy could not be parsed correctly based on http://schemas.xmlsoap.org/ws/2004/09/policy " +
                         "specification";
            log.warn(msg);
            throw new AppManagementException(msg);
        }
    }

    /**
     * The method to extract the tier attributes from each tier level policy definitions
     *
     * @param policy Tier level policy
     * @return Attributes map
     * @throws org.wso2.carbon.appmgt.api.AppManagementException
     */
    public static Map<String, Object> getTierAttributes(OMElement policy)
            throws AppManagementException {
        Map<String, Object> attributesMap = new HashMap<String, Object>();
        OMElement attributes = null;

        try {
            OMElement tier = policy.getFirstChildWithName(AppMConstants.POLICY_ELEMENT).getFirstChildWithName
                    (AppMConstants.THROTTLE_CONTROL_ELEMENT).getFirstChildWithName(AppMConstants.POLICY_ELEMENT).
                    getFirstChildWithName(AppMConstants.POLICY_ELEMENT);
            if (tier != null) {
                attributes = tier.getFirstChildWithName(AppMConstants.THROTTLE_ATTRIBUTES_ELEMENT);
            }
            if (attributes == null) {
                return attributesMap;
            } else {
                for (Iterator childElements = attributes.getChildElements(); childElements.hasNext(); ) {
                    OMElement element = (OMElement) childElements.next();
                    String displayName = element.getAttributeValue(
                            new QName(AppMConstants.THROTTLE_ATTRIBUTE_DISPLAY_NAME));
                    String localName = element.getLocalName();
                    // If displayName not defined,use the attribute name.
                    String attrName = (displayName != null ? displayName : localName);
                    String attrValue = element.getText();
                    attributesMap.put(attrName, attrValue);
                }
            }

        } catch (NullPointerException npe) {
            String msg = "Policy could not be parsed correctly based on http://schemas.xmlsoap.org/ws/2004/09/policy " +
                         "specification";
            log.warn(msg);
            throw new AppManagementException(msg);
        }
        return attributesMap;
    }
}