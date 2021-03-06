/*
*  Copyright (c) 2005-2010, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package barley.appmgt.usage.publisher.dto;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

public class DASRequestPublisherDTO extends RequestPublisherDTO{

    public DASRequestPublisherDTO(RequestPublisherDTO requestPublisherDTO){
        //setConsumerKey(requestPublisherDTO.getConsumerKey());
        setContext(requestPublisherDTO.getContext());
        setApi_version(requestPublisherDTO.getApi_version());
        setApi(requestPublisherDTO.getApi());
        setResource(requestPublisherDTO.getResource());
        setMethod(requestPublisherDTO.getMethod());
        setVersion(requestPublisherDTO.getVersion());
        setRequestTime(requestPublisherDTO.getRequestTime());
        setUsername(requestPublisherDTO.getUsername());
        setTenantDomain(requestPublisherDTO.getTenantDomain());
        setApplicationName(requestPublisherDTO.getApplicationName());
        setApplicationId(requestPublisherDTO.getApplicationId());
        setTrackingCode(requestPublisherDTO.getTrackingCode());
        //setLoggedInUSer(requestPublisherDTO.getLoggedInUSer());
        setReferer(requestPublisherDTO.getReferer());
        setServiceTimeOfPage(requestPublisherDTO.getServiceTimeOfPage());
    }

    public Map<String, ByteBuffer> createEventDataMap() {
        Map<String, ByteBuffer> eventMap = new HashMap<String, ByteBuffer>();
        //eventMap.put("consumerKey", ByteBuffer.wrap(getConsumerKey().getBytes()));
        eventMap.put("context", ByteBuffer.wrap(getContext().getBytes()));
        eventMap.put("api_version", ByteBuffer.wrap(getApi_version().getBytes()));
        eventMap.put("api", ByteBuffer.wrap(getApi().getBytes()));
        eventMap.put("resource", ByteBuffer.wrap(getResource().getBytes()));
        eventMap.put("method", ByteBuffer.wrap(getMethod().getBytes()));
        eventMap.put("version", ByteBuffer.wrap(getVersion().getBytes()));
        eventMap.put("request", ByteBuffer.wrap(String.valueOf(getRequestCount()).getBytes()));
        eventMap.put("requestTime", ByteBuffer.wrap(String.valueOf(getRequestTime()).getBytes()));
        eventMap.put("userId", ByteBuffer.wrap(getUsername().getBytes()));
        eventMap.put("tenantDomain", ByteBuffer.wrap(String.valueOf(getTenantDomain()).getBytes()));
        eventMap.put("applicationName", ByteBuffer.wrap(getApplicationName().getBytes()));
        eventMap.put("applicationId", ByteBuffer.wrap(getApplicationId().getBytes()));
        eventMap.put("trackingCode",ByteBuffer.wrap(getTrackingCode().getBytes()));
        eventMap.put("referer",ByteBuffer.wrap(getReferer().getBytes()));
        eventMap.put("serviceTimeOfPage",ByteBuffer.wrap(String.valueOf(getServiceTimeOfPage()).getBytes()));

        return eventMap;
    }

}
