/*
*  Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
*
*  WSO2 Inc. licenses this file to you under the Apache License,
*  Version 2.0 (the "License"); you may not use this file except
*  in compliance with the License.
*  You may obtain a copy of the License at
*
*    http://www.apache.org/licenses/LICENSE-2.0
*
*  Unless required by applicable law or agreed to in writing,
*  software distributed under the License is distributed on an
*  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
*  KIND, either express or implied.  See the License for the
*  specific language governing permissions and limitations
*  under the License.
*
*/

package barley.appmgt.gateway.internal;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import javax.xml.stream.XMLStreamException;

import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axis2.AxisFault;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.synapse.ServerContextInformation;
import org.apache.synapse.config.SynapseConfiguration;
import org.apache.synapse.config.SynapseConfigurationBuilder;
import org.apache.synapse.config.xml.MultiXMLConfigurationBuilder;
import org.apache.synapse.config.xml.SequenceMediatorFactory;
import org.apache.synapse.mediators.base.SequenceMediator;
//import org.wso2.carbon.mediation.initializer.configurations.ConfigurationInitilizerException;
//import org.wso2.carbon.mediation.initializer.configurations.ConfigurationManager;
//import org.wso2.carbon.mediation.registry.WSO2Registry;

import barley.appmgt.impl.AppMConstants;
import barley.appmgt.impl.utils.AppManagerUtil;
import barley.core.utils.AbstractAxis2ConfigurationContextObserver;
import barley.core.utils.BarleyUtils;


/**
 * This creates the {@link org.apache.synapse.config.SynapseConfiguration}
 * for the respective tenants. This class specifically add to deploy WebApp Manager
 * related synapse sequences. This class used to deploy resource mismatch handler, auth failure handler,
 * sandbox error handler, throttle out handler, build sequence, main sequence and fault sequence into tenant
 * synapse artifact space.
 */
public class TenantCreateGatewayObserver extends AbstractAxis2ConfigurationContextObserver {
    private static final Log log = LogFactory.getLog(TenantCreateGatewayObserver.class);
    private String resourceMisMatchSequenceName = "_resource_mismatch_handler_";
    private String throttleOutSequenceName = "_throttle_out_handler_";
    private String buildSequenceName = "_build_";
    private String faultSequenceName = "fault";
    private String mainSequenceName = "main";
    private String saml2SequenceName = "saml2_sequence";
    
    // (수정) 2019.09.18 - 시스템 변수를 프로젝트 별 변수로 변경 
    //private String synapseConfigRootPath = BarleyUtils.getCarbonHome() + AppMConstants.SYNAPSE_CONFIG_RESOURCES_PATH;
    private String synapseConfigRootPath = AppManagerUtil.getAppManagerHome() + AppMConstants.SYNAPSE_CONFIG_RESOURCES_PATH;
    
    private SequenceMediator authFailureHandlerSequence = null;
    private SequenceMediator resourceMisMatchSequence = null;
    private SequenceMediator throttleOutSequence = null;
    private SequenceMediator sandboxKeyErrorSequence = null;
    private SequenceMediator productionKeyErrorSequence = null;


    public void createdConfigurationContext(ConfigurationContext configurationContext) {
    	/* (임시주석)
        String tenantDomain = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantDomain();
    	int tenantId = PrivilegedCarbonContext.getThreadLocalCarbonContext().getTenantId();
        try {
            // first check which configuration should be active
            org.wso2.carbon.registry.core.Registry registry =
                    (org.wso2.carbon.registry.core.Registry) PrivilegedCarbonContext.getThreadLocalCarbonContext()
                            .getRegistry(RegistryType.SYSTEM_CONFIGURATION);

            AxisConfiguration axisConfig = configurationContext.getAxisConfiguration();

            // initialize the lock
            Lock lock = new ReentrantLock();
            axisConfig.addParameter("synapse.config.lock", lock);

            // creates the synapse configuration directory hierarchy if not exists
            // useful at the initial tenant creation
            File tenantAxis2Repo = new File(configurationContext.getAxisConfiguration().getRepository().getFile());
            File synapseConfigsDir = new File(tenantAxis2Repo, "synapse-configs");
            if (!synapseConfigsDir.exists()) {
                if (!synapseConfigsDir.mkdir()) {
                    log.fatal("Couldn't create the synapse-config root on the file system for the tenant domain : "
                                      + tenantDomain);
                    return;
                }
            }

            String synapseConfigsDirLocation = synapseConfigsDir.getAbsolutePath();
            // set the required configuration parameters to initialize the ESB
            axisConfig.addParameter(SynapseConstants.Axis2Param.SYNAPSE_CONFIG_LOCATION, synapseConfigsDirLocation);

            // init the multiple configuration tracker
            ConfigurationManager manger = new ConfigurationManager((UserRegistry) registry, configurationContext);
            manger.init();

            File synapseConfigDir = new File(synapseConfigsDir, manger.getTracker().getCurrentConfigurationName());
            File buildSequenceFile = new File(synapseConfigsDir + File.separator + manger.getTracker()
                    .getCurrentConfigurationName() + File.separator + MultiXMLConfigurationBuilder.SEQUENCES_DIR +
                                                      File.separator + buildSequenceName + ".xml");

            //Here we will check build sequence exist in synapse artifact. If it is not available we will create
            //sequence synapse configurations by using resource artifacts
            if (!buildSequenceFile.exists()) {
                createTenantSynapseConfigHierarchy(synapseConfigDir, tenantDomain);
            }            
        } catch (AxisFault e) {
             log.error("Failed to create Tenant's synapse sequences for tenant " + tenantDomain);
        } catch (ConfigurationInitilizerException e) {
            log.error("Failed to create Tenant's synapse sequences for tenant. ");
        }

        try{
            AppManagerUtil.loadTenantAPIPolicy(tenantDomain, tenantId);
        }catch (AppManagementException e){
            log.error("Failed to load tiers.xml to tenant's registry");
        }
        */
    }


    private ServerContextInformation initESB(String configurationName, ConfigurationContext configurationContext)
            throws AxisFault {
        return null;
    }

    /**
     * Create the file system for holding the synapse configuration for a new tenant.
     *
     * @param synapseConfigDir configuration directory where synapse configuration is created
     * @param tenantDomain     name of the tenant
     */
    @SuppressWarnings({"ResultOfMethodCallIgnored"})
    private void createTenantSynapseConfigHierarchy(File synapseConfigDir, String tenantDomain) {
        synapseConfigDir.mkdir();
        File sequencesDir = new File(synapseConfigDir, MultiXMLConfigurationBuilder.SEQUENCES_DIR);

        if (!sequencesDir.mkdir()) {
            log.warn("Could not create synapse configuration for tenant" + tenantDomain);
        }

        SynapseConfiguration initialSynapseConfig = SynapseConfigurationBuilder.getDefaultConfiguration();
        InputStream in = null;
        StAXOMBuilder builder = null;
        SequenceMediatorFactory factory = new SequenceMediatorFactory();
        try {
            if (resourceMisMatchSequence == null) {
                in = FileUtils.openInputStream(new File(synapseConfigRootPath, resourceMisMatchSequenceName + ".xml"));
                builder = new StAXOMBuilder(in);
                resourceMisMatchSequence = (SequenceMediator) factory.createMediator(builder.getDocumentElement(), new Properties());
                resourceMisMatchSequence.setFileName(resourceMisMatchSequenceName + ".xml");
            }
            if (throttleOutSequence == null) {
                in = FileUtils.openInputStream(new File(synapseConfigRootPath, throttleOutSequenceName + ".xml"));
                builder = new StAXOMBuilder(in);
                throttleOutSequence = (SequenceMediator) factory.createMediator(builder.getDocumentElement(), new Properties());
                throttleOutSequence.setFileName(throttleOutSequenceName + ".xml");
            }
            FileUtils.copyFile(new File(synapseConfigRootPath + mainSequenceName + ".xml"),
                    new File(synapseConfigDir.getAbsolutePath() + File.separator + "sequences" + File.separator + mainSequenceName + ".xml"));

            FileUtils.copyFile(new File(synapseConfigRootPath + faultSequenceName + ".xml"),
                    new File(synapseConfigDir.getAbsolutePath() + File.separator + "sequences" + File.separator + faultSequenceName + ".xml"));
            FileUtils.copyFile(new File(synapseConfigRootPath + saml2SequenceName + ".xml"),
                    new File(synapseConfigDir.getAbsolutePath() + File.separator + "sequences" + File.separator + saml2SequenceName + ".xml"));

        } catch (IOException e) {                                                             
            log.error("Error while reading WebApp manager specific synapse sequences" + e);
        } catch (XMLStreamException e) {
            log.error("Error while parsing WebApp manager specific synapse sequences" + e);
        } finally {
            IOUtils.closeQuietly(in);
        }

        // (임시주석)
        /*
        Registry registry = new WSO2Registry();
        initialSynapseConfig.setRegistry(registry);
        MultiXMLConfigurationSerializer serializer = new MultiXMLConfigurationSerializer(synapseConfigDir
                                                                                                 .getAbsolutePath());
        try {
            serializer.serializeSequence(throttleOutSequence, initialSynapseConfig, null);
            serializer.serializeSequence(resourceMisMatchSequence, initialSynapseConfig, null);
            serializer.serializeSynapseRegistry(registry, initialSynapseConfig, null);
        } catch (Exception e) {
            handleException("Couldn't serialise the initial synapse configuration for the domain : " + tenantDomain, e);
        }
        */
    }

    public static boolean isRunningSamplesMode() {
        return true;
    }

    private void handleException(String message, Exception e) {
        log.error(message, e);
    }
}
