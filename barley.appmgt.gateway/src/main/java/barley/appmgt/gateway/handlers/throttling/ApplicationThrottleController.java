package barley.appmgt.gateway.handlers.throttling;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.activation.DataHandler;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import org.apache.axiom.om.OMAbstractFactory;
import org.apache.axiom.om.OMElement;
import org.apache.axiom.om.OMException;
import org.apache.axiom.om.OMFactory;
import org.apache.axiom.om.OMNode;
import org.apache.axiom.om.impl.builder.StAXOMBuilder;
import org.apache.axiom.om.impl.llom.OMDocumentImpl;
import org.apache.axis2.context.ConfigurationContext;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.neethi.PolicyEngine;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseException;
import org.apache.synapse.commons.throttle.core.Throttle;
import org.apache.synapse.commons.throttle.core.ThrottleConstants;
import org.apache.synapse.commons.throttle.core.ThrottleContext;
import org.apache.synapse.commons.throttle.core.ThrottleException;
import org.apache.synapse.commons.throttle.core.ThrottleFactory;
import org.apache.synapse.util.SynapseBinaryDataSource;

import barley.core.MultitenantConstants;
import barley.mediation.registry.RegistryServiceHolder;
import barley.registry.core.Collection;
import barley.registry.core.Registry;
import barley.registry.core.RegistryConstants;
import barley.registry.core.Resource;
import barley.registry.core.exceptions.RegistryException;
import barley.registry.core.service.RegistryService;

public class ApplicationThrottleController {
    
    public static final String APPLICATION_THROTTLE_POLICY_KEY = "gov:/appmgt/applicationdata/tiers.xml";

    public static final String APP_THROTTLE_CONTEXT_PREFIX = "APP_THROTTLE_CONTEXT_";

    public static final String GOVERNANCE_REGISTRY_PREFIX = "gov:";

    private static final Log log = LogFactory.getLog(ApplicationThrottleController.class);

    private static final Object lock = new Object();
    
    public static ThrottleContext getApplicationThrottleContext(MessageContext synCtx, ConfigurationContext cc,
                                                                String applicationId){
        synchronized (lock) {
            Object throttleContext = cc.getProperty(APP_THROTTLE_CONTEXT_PREFIX + applicationId);
            if(throttleContext == null){
                return createThrottleContext(synCtx, cc, applicationId);
            }
            return (ThrottleContext)throttleContext;
        }
    }

    private static ThrottleContext createThrottleContext(MessageContext synCtx, ConfigurationContext cc, String applicationId){

        //Entry entry = synCtx.getConfiguration().getEntryDefinition(APPLICATION_THROTTLE_POLICY_KEY);
        //if (entry == null) {
        //    handleException("Cannot find throttling policy using key: " + APPLICATION_THROTTLE_POLICY_KEY);
        //    return null;
        //}

        //Object entryValue = synCtx.getEntry(APPLICATION_THROTTLE_POLICY_KEY);
        Object entryValue = lookup(APPLICATION_THROTTLE_POLICY_KEY);
        if (entryValue == null || !(entryValue instanceof OMElement)) {
            handleException("Unable to load throttling policy using key: " + APPLICATION_THROTTLE_POLICY_KEY);
            return null;
        }

        try {
            Throttle throttle = ThrottleFactory.createMediatorThrottle(PolicyEngine.getPolicy((OMElement) entryValue));
            ThrottleContext context = throttle.getThrottleContext(ThrottleConstants.ROLE_BASED_THROTTLE_KEY);
            cc.setProperty(APP_THROTTLE_CONTEXT_PREFIX + applicationId, context);
            return context;
        } catch (ThrottleException e) {
            handleException("Error processing the throttling policy", e);
        }
        return null;
    }

    private static void handleException(String msg) {
        log.error(msg);
        throw new SynapseException(msg);
    }

    private static void handleException(String msg, Exception e) {
        log.error(msg, e);
        throw new SynapseException(msg, e);
    }
    
    private static OMNode lookup(String key){
        try {
            Resource resource = getResource(key);

            if (resource instanceof Collection || resource == null) {
                return null;
            }

            ByteArrayInputStream inputStream = null;
            Object content = resource.getContent();
            if (content instanceof String) {
                inputStream = new ByteArrayInputStream(content.toString().getBytes());
            } else if (content instanceof byte[]) {
                inputStream = new ByteArrayInputStream((byte[]) content);
            }

            OMNode result = null;
            try {
                XMLStreamReader parser = XMLInputFactory.newInstance().
                        createXMLStreamReader(inputStream);
                StAXOMBuilder builder = new StAXOMBuilder(parser);
                result = builder.getDocumentElement();

            } catch (OMException ignored) {
                result = readNonXML(resource);

            } catch (XMLStreamException ignored) {
                result = readNonXML(resource);

            } catch (Exception e) {
                // a more general exception(e.g. a Runtime exception if the XML doc has an
                // external DTD deceleration and if not connected to internet) which in case
                // just log for debugging
                log.error("Error while reading the resource '" + key + "'", e);
            } finally {
                try {
                    resource.discard();
                    if (result != null && result.getParent() != null) {
                        result.detach();
                        OMDocumentImpl parent = new OMDocumentImpl(OMAbstractFactory.getOMFactory());
                        parent.addChild(result);
                    }
                    if (inputStream != null) {
                        inputStream.close();
                    }
                } catch (IOException e) {
                    log.error("Error while closing the input stream", e);
                }
            }
            return result;

        } catch (RegistryException e) {
            handleException("Error while fetching the resource " + key, e);
        }

        return null;
    }

    private static OMNode readNonXML(Resource resource) throws RegistryException {

        if (log.isDebugEnabled()) {
            log.debug("The resource at the specified path does not contain " +
                    "well-formed XML - Processing as text");
        }

        if (resource != null) {
            if (resource.getMediaType().equals("text/plain")) {
                // for non-xml text content
                return OMAbstractFactory.getOMFactory().createOMText(
                        new String((byte[]) resource.getContent()));
            }

            ByteArrayInputStream inputStream = new ByteArrayInputStream(
                    (byte[]) resource.getContent());
            try {
                OMFactory omFactory = OMAbstractFactory.getOMFactory();
                return omFactory.createOMText(
                        new DataHandler(new SynapseBinaryDataSource(inputStream,
                                resource.getMediaType())), true);
            } catch (IOException e) {
                handleException("Error while getting a stream from resource content ", e);
            } finally {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    log.error("Error while closing the input stream", e);
                }
            }
        }
        return null;
    }

    private static Resource getResource(String path) {

        RegistryService registryService = RegistryServiceHolder.getInstance().getRegistryService();

        Registry registry = null;
        try {
            registry = registryService.getGovernanceSystemRegistry(MultitenantConstants.SUPER_TENANT_ID);
        } catch (RegistryException e) {
            log.error("Error while fetching Governance Registry of Super Tenant");
            return null;
        }

        String key = resolvePath(path);

        try {
            if (registry.resourceExists(key)) {
                return registry.get(key);
            }
        } catch (RegistryException e) {
            handleException("Error while fetching the resource " + path, e);
        }
        return null;
    }

    private static String resolvePath(String path) {
        if (path == null || "".equals(path)) {
            path = RegistryConstants.ROOT_PATH;
        }

        path = path.substring(GOVERNANCE_REGISTRY_PREFIX.length());

        if (path.startsWith(RegistryConstants.PATH_SEPARATOR)) {
                path = path.substring(1);
        }
        path = RegistryConstants.ROOT_PATH + path;
        return path;
    }
}
