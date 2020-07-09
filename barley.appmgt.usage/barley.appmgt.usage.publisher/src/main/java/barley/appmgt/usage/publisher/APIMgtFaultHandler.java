package barley.appmgt.usage.publisher;

import java.util.Date;

import barley.appmgt.usage.publisher.internal.UsageComponent;
import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.mediators.AbstractMediator;

import barley.appmgt.usage.publisher.dto.FaultPublisherDTO;
import barley.appmgt.usage.publisher.internal.APPManagerConfigurationServiceComponent;
import barley.core.multitenancy.MultitenantUtils;

public class APIMgtFaultHandler extends AbstractMediator{

	private volatile APIMgtUsageDataPublisher publisher;

    @Override
    public boolean mediate(MessageContext messageContext) {
    	try{
            if (!enabled()) {
                return true;
            }

            if(publisher == null) {
                this.loadDataPublisher();
            }

            long requestTime = 0;
            //If request time is null in the message context,this code paragraph will set the system time as request time
            //In here request time can be null because in happy case senario request time will be set in the Usage handler
            if(messageContext.getProperty(APIMgtUsagePublisherConstants.REQUEST_TIME) != null) {
                requestTime =  ((Long) messageContext.getProperty(APIMgtUsagePublisherConstants.REQUEST_TIME)).longValue();
            } else {
                Date date = new Date();
                requestTime = date.getTime();
            }

            // request(APIMgtUsageHandler)에서 처리한 messageContext에 저장된 값을 가져온다.
            FaultPublisherDTO faultPublisherDTO = new FaultPublisherDTO();
            String userId = (String) messageContext.getProperty(APIMgtUsagePublisherConstants.USER_ID);
            String appName = (String) messageContext.getProperty(APIMgtUsagePublisherConstants.API);
            if(appName != null) {
                faultPublisherDTO.setContext((String) messageContext.getProperty(APIMgtUsagePublisherConstants.CONTEXT));
                faultPublisherDTO.setApi_version((String) messageContext.getProperty(APIMgtUsagePublisherConstants.APP_VERSION));
                faultPublisherDTO.setApi(appName);
                faultPublisherDTO.setResource((String) messageContext.getProperty(APIMgtUsagePublisherConstants.RESOURCE));
                faultPublisherDTO.setMethod((String) messageContext.getProperty(APIMgtUsagePublisherConstants.HTTP_METHOD));
                faultPublisherDTO.setVersion((String) messageContext.getProperty(APIMgtUsagePublisherConstants.VERSION));
                faultPublisherDTO.setErrorCode(String.valueOf(messageContext.getProperty(SynapseConstants.ERROR_CODE)));
                faultPublisherDTO.setErrorMessage((String) messageContext.getProperty(SynapseConstants.ERROR_MESSAGE));
                faultPublisherDTO.setRequestTime(requestTime);
                faultPublisherDTO.setUsername(userId);
                faultPublisherDTO.setTenantDomain(DataPublisherUtil.getTenantDomain(messageContext));
                faultPublisherDTO.setHostName((String) messageContext.getProperty(APIMgtUsagePublisherConstants.HOST_NAME));
                faultPublisherDTO.setApiPublisher((String) messageContext.getProperty(APIMgtUsagePublisherConstants.API_PUBLISHER));
                faultPublisherDTO.setApplicationName((String) messageContext.getProperty(APIMgtUsagePublisherConstants.APPLICATION_NAME));
                faultPublisherDTO.setApplicationId((String) messageContext.getProperty(APIMgtUsagePublisherConstants.APPLICATION_ID));
                faultPublisherDTO.setTrackingCode((String) messageContext.getProperty(APIMgtUsagePublisherConstants.TRACKING_CODE));
                faultPublisherDTO.setReferer((String) messageContext.getProperty(APIMgtUsagePublisherConstants.REFERER));
                publisher.publishEvent(faultPublisherDTO);
            }

        } catch (Throwable e) {
            log.error("Cannot publish event. " + e.getMessage(), e);
        }
        return true; // Should never stop the message flow
    }
    
    private boolean enabled() {
    	return UsageComponent.getApiMgtConfigReaderService().isEnabled();
    }

    private void loadDataPublisher() {
        String publisherClass = UsageComponent.getApiMgtConfigReaderService().getPublisherClass();

        if (publisher == null) {
            synchronized (this) {
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
