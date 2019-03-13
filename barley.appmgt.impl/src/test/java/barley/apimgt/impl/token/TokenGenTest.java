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

package barley.apimgt.impl.token;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import barley.appmgt.impl.AppManagerConfiguration;
import barley.appmgt.impl.AppManagerConfigurationServiceImpl;
import barley.appmgt.impl.service.ServiceReferenceHolder;
import junit.framework.TestCase;


public class TokenGenTest extends TestCase {
    private static final Log log = LogFactory.getLog(TokenGenTest.class);

    @Override
    protected void setUp() throws Exception {
        String dbConfigPath = System.getProperty("APIManagerDBConfigurationPath");
        AppManagerConfiguration config = new AppManagerConfiguration();
        config.load(dbConfigPath);
        ServiceReferenceHolder.getInstance().setAPIManagerConfigurationService(
                new AppManagerConfigurationServiceImpl(config));
    }
}
