/*
 * Copyright WSO2 Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package barley.appmgt.gateway.handlers.throttling;

public class APIThrottleConstants {

	public static final int THROTTLE_OUT_ERROR_CODE = 900800;

	public static final String API_THROTTLE_NS = "http://wso2.org/appmanager/throttling";
	public static final String API_THROTTLE_NS_PREFIX = "amt";

	public static final String API_THROTTLE_OUT_HANDLER = "_throttle_out_handler_";

	public static final String URL_MAPPING_ALL = "/*";
	public static final String URL_MAPPING_SEPERATOR = "/";
	public static final String URL_MAPPING_COLON = "/*";

}
