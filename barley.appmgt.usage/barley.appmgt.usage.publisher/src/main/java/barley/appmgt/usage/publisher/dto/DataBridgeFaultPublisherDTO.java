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

import barley.appmgt.usage.publisher.DataPublisherUtil;
import barley.appmgt.usage.publisher.internal.APPManagerConfigurationServiceComponent;
import barley.appmgt.usage.publisher.internal.UsageComponent;

public class DataBridgeFaultPublisherDTO extends FaultPublisherDTO{

    public DataBridgeFaultPublisherDTO(FaultPublisherDTO faultPublisherDTO){
       // setConsumerKey(faultPublisherDTO.getConsumerKey());
        setContext(faultPublisherDTO.getContext());
        setApi_version(faultPublisherDTO.getApi_version());
        setApi(faultPublisherDTO.getApi());
        setResource(faultPublisherDTO.getResource());
        setMethod(faultPublisherDTO.getMethod());
        setVersion(faultPublisherDTO.getVersion());
        setErrorCode(faultPublisherDTO.getErrorCode());
        setErrorMessage(faultPublisherDTO.getErrorMessage());
        setRequestTime((faultPublisherDTO.getRequestTime()));
        setUsername(faultPublisherDTO.getUsername());
        setTenantDomain(faultPublisherDTO.getTenantDomain());
        setHostName(DataPublisherUtil.getHostAddress());
        setApiPublisher(faultPublisherDTO.getApiPublisher());
        setApplicationName(faultPublisherDTO.getApplicationName());
        setApplicationId(faultPublisherDTO.getApplicationId());
        setTrackingCode(faultPublisherDTO.getTrackingCode());
        //setLoggedInUSer(faultPublisherDTO.getLoggedInUSer());
        setReferer(faultPublisherDTO.getReferer());
        setResponseTime(faultPublisherDTO.getResponseTime());
    }

    public static String getStreamDefinition() {

		String streamDefinition = "{" + "  'name':'"
				+ UsageComponent
						.getApiMgtConfigReaderService()
						.getApiManagerFaultStreamName()
				+ "',"
				+ "  'version':'"
				+ UsageComponent
						.getApiMgtConfigReaderService()
						.getApiManagerFaultStreamVersion() + "',"
				+ "  'nickName': 'WebApp Manager Fault Data',"
				+ "  'description': 'Fault Data'," + "  'metaData':["
				+ "          {'name':'clientType','type':'STRING'}" + "  ],"
				+ "  'payloadData':[" +

				"          {'name':'context','type':'STRING'},"
				+ "          {'name':'api_version','type':'STRING'},"
				+ "          {'name':'api','type':'STRING'},"
				+ "          {'name':'resource','type':'STRING'},"
				+ "          {'name':'method','type':'STRING'},"
				+ "          {'name':'version','type':'STRING'},"
				+ "          {'name':'errorCode','type':'STRING'},"
				+ "          {'name':'errorMessage','type':'STRING'},"
				+ "          {'name':'requestTime','type':'STRING'},"
				+ "          {'name':'userId','type':'STRING'},"
				+ "          {'name':'tenantDomain','type':'STRING'},"
				+ "          {'name':'hostName','type':'STRING'},"
				+ "          {'name':'apiPublisher','type':'STRING'},"
				+ "          {'name':'applicationName','type':'STRING'},"
				+ "          {'name':'applicationId','type':'STRING'},"
				+ "          {'name':'trackingCode','type':'STRING'},"
				+ "          {'name':'referer','type':'STRING'},"
				+ "          {'name':'responseTime','type':'LONG'}" +

				"  ]" +

				"}";

        return streamDefinition;
    }

    public Object createPayload(){
        return new Object[]{getContext(),getApi_version(),getApi(),getResource(),getMethod(),
                getVersion(),getErrorCode(),getErrorMessage(), String.valueOf(getRequestTime()),getUsername(),
                getTenantDomain(),getHostName(),getApiPublisher(), getApplicationName(), getApplicationId(),getTrackingCode(),getReferer()};
    }
}
