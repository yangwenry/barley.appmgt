package barley.appmgt.usage.publisher;

import barley.appmgt.gateway.APIMgtGatewayConstants;
import barley.appmgt.usage.publisher.dto.ResponsePublisherDTO;
import barley.appmgt.usage.publisher.internal.UsageComponent;
import barley.core.multitenancy.MultitenantUtils;
import org.apache.axiom.soap.SOAPBody;
import org.apache.axiom.soap.SOAPEnvelope;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.http.HttpHeaders;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.core.axis2.Axis2MessageContext;
import org.apache.synapse.mediators.AbstractMediator;
import org.apache.synapse.transport.passthru.util.RelayUtils;

import javax.xml.stream.XMLStreamException;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.Map;

public class APIMgtResponseHandler extends AbstractMediator {

    private static final Log log   = LogFactory.getLog(APIMgtResponseHandler.class);

    private volatile APIMgtUsageDataPublisher publisher;

    @Override
    public boolean mediate(MessageContext mc) {
        boolean enabled = UsageComponent.getApiMgtConfigReaderService().isEnabled();

        if (!enabled) {
            return true;
        }

        if(publisher == null) {
            this.loadDataPublisher();
        }

        long responseSize = 0;
        long responseTime = 0;
        long serviceTime = 0;
        long backendTime = 0;
        long endTime = System.currentTimeMillis();

        Object startTimeProperty = mc.getProperty(APIMgtGatewayConstants.REQUEST_START_TIME);
        long startTime = (startTimeProperty == null ? 0 :  ((Number) startTimeProperty)).longValue();

        Object beStartTimeProperty = mc.getProperty(APIMgtGatewayConstants.BACKEND_REQUEST_START_TIME);
        long backendStartTime = (beStartTimeProperty == null ? 0 : ((Number) beStartTimeProperty)).longValue();

        Object beEndTimeProperty = mc.getProperty(APIMgtGatewayConstants.BACKEND_REQUEST_END_TIME);
        long backendEndTime = (beEndTimeProperty == null ? 0 : ((Number) beEndTimeProperty).longValue());

        //boolean isBuildMsg = UsageComponent.getAmConfigService().getAPIAnalyticsConfiguration().isBuildMsg();
        boolean isBuildMsg = true;
        org.apache.axis2.context.MessageContext axis2MC = ((Axis2MessageContext) mc).
                getAxis2MessageContext();
        if (isBuildMsg) {
            Map headers = (Map) axis2MC.getProperty(
                    org.apache.axis2.context.MessageContext.TRANSPORT_HEADERS);
            String contentLength = (String) headers.get(HttpHeaders.CONTENT_LENGTH);
            if (contentLength != null) {
                responseSize = Integer.parseInt(contentLength);
            } else {  //When chunking is enabled
                try {
                    RelayUtils.buildMessage(axis2MC);
                } catch (IOException ex) {
                    //In case of an exception, it won't be propagated up,and set response size to 0
                    log.error("Error occurred while building the message to" +
                            " calculate the response body size", ex);
                } catch (XMLStreamException ex) {
                    log.error("Error occurred while building the message to calculate the response" +
                            " body size", ex);
                }

                SOAPEnvelope env = mc.getEnvelope();
                if (env != null) {
                    SOAPBody soapbody = env.getBody();
                    if (soapbody != null) {
                        byte[] size = soapbody.toString().getBytes(Charset.defaultCharset());
                        responseSize = size.length;
                    }
                }

            }
        }

        // request(APIMgtUsageHandler)에서 처리한 messageContext에 저장된 값을 가져온다.
        String context = (String) mc.getProperty(APIMgtUsagePublisherConstants.CONTEXT);
        String userId = (String) mc.getProperty(APIMgtUsagePublisherConstants.USER_ID);
        String apiVersion = (String) mc.getProperty(APIMgtUsagePublisherConstants.APP_VERSION);
        String appName = (String) mc.getProperty(APIMgtUsagePublisherConstants.API);
        String version = (String) mc.getProperty(APIMgtUsagePublisherConstants.VERSION);
        String resource = (String) mc.getProperty(APIMgtUsagePublisherConstants.RESOURCE);
        String httpMethod = (String) mc.getProperty(APIMgtUsagePublisherConstants.HTTP_METHOD);
        String hostName = (String) mc.getProperty(APIMgtUsagePublisherConstants.HOST_NAME);
        String applicationName = (String) mc.getProperty(APIMgtUsagePublisherConstants.APPLICATION_NAME);
        String applicationId = (String) mc.getProperty(APIMgtUsagePublisherConstants.APPLICATION_ID);
        String trackingCode = (String) mc.getProperty(APIMgtUsagePublisherConstants.TRACKING_CODE);
        String referer = (String) mc.getProperty(APIMgtUsagePublisherConstants.REFERER);
        String apiPublisher = (String) mc.getProperty(APIMgtUsagePublisherConstants.API_PUBLISHER);
        String tenantDomain = DataPublisherUtil.getTenantDomain(mc);

        //When start time not properly set
        if (startTime == 0) {
            responseTime = 0;
            backendTime = 0;
            serviceTime = 0;
        } else if (endTime != 0 && backendStartTime != 0 && backendEndTime != 0) { //When
            // response caching is disabled
            responseTime = endTime - startTime;
            backendTime = backendEndTime - backendStartTime;
            serviceTime = responseTime - backendTime;

        } else if (endTime != 0 && backendStartTime == 0) {//When response caching enabled
            responseTime = endTime - startTime;
            serviceTime = responseTime;
            backendTime = 0;
        }

        ResponsePublisherDTO responsePublisherDTO = new ResponsePublisherDTO();
        responsePublisherDTO.setUsername(userId);
        responsePublisherDTO.setContext(context);
        responsePublisherDTO.setApi_version(apiVersion);
        responsePublisherDTO.setApi(appName);
        responsePublisherDTO.setVersion(version);
        responsePublisherDTO.setResource(resource);
        responsePublisherDTO.setMethod(httpMethod);
        responsePublisherDTO.setResponseTime(responseTime);
        responsePublisherDTO.setServiceTime(serviceTime);
        responsePublisherDTO.setEventTime(endTime);
        responsePublisherDTO.setHostName(hostName);
        responsePublisherDTO.setApiPublisher(apiPublisher);
        responsePublisherDTO.setTenantDomain(tenantDomain);
        responsePublisherDTO.setApplicationId(applicationId);
        responsePublisherDTO.setApplicationName(applicationName);
        responsePublisherDTO.setTrackingCode(trackingCode);
        responsePublisherDTO.setReferer(referer);
        responsePublisherDTO.setResponseSize(responseSize);
        responsePublisherDTO.setResponseCode((Integer) axis2MC.getProperty(SynapseConstants.HTTP_SC));

        try {
            publisher.publishEvent(responsePublisherDTO);
        } catch (Exception e) {
            log.error("Cannot publish event. " + e.getMessage(), e);
        }
        return true;
    }

    private void loadDataPublisher() {
        String publisherClass = UsageComponent.getApiMgtConfigReaderService().getPublisherClass();

        if (publisher == null) {
            synchronized (this){
                if (publisher == null) {
                    try {
                        log.debug("Instantiating Data Publisher");
                        publisher = (APIMgtUsageDataPublisher)Class.forName(publisherClass).newInstance();
                        publisher.init();
                    } catch (ClassNotFoundException e) {
                        log.error("Class not found " + publisherClass);
                    } catch (InstantiationException e) {
                        log.error("Error instantiating " + publisherClass);
                    } catch (IllegalAccessException e) {
                        log.error("Illegal access to " + publisherClass);
                    }
                }
            }
        }
    }


}
