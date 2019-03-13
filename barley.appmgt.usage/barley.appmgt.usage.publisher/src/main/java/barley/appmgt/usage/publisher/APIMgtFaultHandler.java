package barley.appmgt.usage.publisher;

import java.util.Date;

import org.apache.synapse.MessageContext;
import org.apache.synapse.SynapseConstants;
import org.apache.synapse.mediators.AbstractMediator;

import barley.appmgt.usage.publisher.dto.FaultPublisherDTO;
import barley.appmgt.usage.publisher.internal.APPManagerConfigurationServiceComponent;
import barley.core.multitenancy.MultitenantUtils;

public class APIMgtFaultHandler extends AbstractMediator{

	// (수정) 2018.04.02 - 전역변수를 함수로 변경 
    //private boolean enabled = APPManagerConfigurationServiceComponent.getApiMgtConfigReaderService().isEnabled();
    private final String SUBJECT = "Subject";

    private volatile APIMgtUsageDataPublisher publisher;

    // (수정) 2018.04.02 - 전역변수를 함수로 변경
//    private String publisherClass = APPManagerConfigurationServiceComponent.getApiMgtConfigReaderService().
//            getPublisherClass();

    public APIMgtFaultHandler(){
    	// (주석)
    	/*
    	if (!enabled()) {
            return;
        }

        if (publisher == null) {
            synchronized (this){
                if (publisher == null) {
                	// (추가)
                	String publisherClass = getPublisherClass();
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
        */
    }

    public boolean mediate(MessageContext messageContext) {
    	// (추가)
    	//activeConfiguration();
    	loadPublisherClass();
    	try{
            if (!enabled()) {
                return true;
            }
            long requestTime = 0;
            //If request time is null in the message context,this code paragraph will set the system time as request time
            //In here request time can be null because in happy case senario request time will be set in the Usage handler
            if(messageContext.getProperty(APIMgtUsagePublisherConstants.REQUEST_TIME) != null){
              requestTime =  ((Long) messageContext.getProperty(APIMgtUsagePublisherConstants.REQUEST_TIME)).longValue();
            }else{
                Date date = new Date();
                requestTime = date.getTime();
            }

            FaultPublisherDTO faultPublisherDTO = new FaultPublisherDTO();
            //faultPublisherDTO.setConsumerKey((String) messageContext.getProperty(APIMgtUsagePublisherConstants.CONSUMER_KEY));
            faultPublisherDTO.setContext((String) messageContext.getProperty(APIMgtUsagePublisherConstants.CONTEXT));
            faultPublisherDTO.setApi_version((String) messageContext.getProperty(APIMgtUsagePublisherConstants.APP_VERSION));
            faultPublisherDTO.setApi((String) messageContext.getProperty(APIMgtUsagePublisherConstants.API));
            faultPublisherDTO.setResource((String) messageContext.getProperty(APIMgtUsagePublisherConstants.RESOURCE));
            faultPublisherDTO.setMethod((String) messageContext.getProperty(APIMgtUsagePublisherConstants.HTTP_METHOD));
            faultPublisherDTO.setVersion((String) messageContext.getProperty(APIMgtUsagePublisherConstants.VERSION));
            faultPublisherDTO.setErrorCode(String.valueOf(messageContext.getProperty(SynapseConstants.ERROR_CODE)));
            faultPublisherDTO.setErrorMessage((String) messageContext.getProperty(SynapseConstants.ERROR_MESSAGE));
            faultPublisherDTO.setRequestTime(requestTime);
            faultPublisherDTO.setUsername((String) messageContext.getProperty(SUBJECT));
            faultPublisherDTO.setTenantDomain(MultitenantUtils.getTenantDomain(faultPublisherDTO.getUsername()));
            faultPublisherDTO.setHostName((String) messageContext.getProperty(APIMgtUsagePublisherConstants.HOST_NAME));
            faultPublisherDTO.setApiPublisher((String) messageContext.getProperty(APIMgtUsagePublisherConstants.API_PUBLISHER));
            faultPublisherDTO.setApplicationName((String) messageContext.getProperty(APIMgtUsagePublisherConstants.APPLICATION_NAME));
            faultPublisherDTO.setApplicationId((String) messageContext.getProperty(APIMgtUsagePublisherConstants.APPLICATION_ID));
            faultPublisherDTO.setTrackingCode((String) messageContext.getProperty(APIMgtUsagePublisherConstants.TRACKING_CODE));
            faultPublisherDTO.setReferer((String) messageContext.getProperty(APIMgtUsagePublisherConstants.REFERER));
            publisher.publishEvent(faultPublisherDTO);

        }catch (Throwable e){
            log.error("Cannot publish event. " + e.getMessage(), e);
        }
        return true; // Should never stop the message flow
    }
    
    private void activeConfiguration() {
    	APPManagerConfigurationServiceComponent component = new APPManagerConfigurationServiceComponent();
		try {
			component.activate();
		} catch (Exception e) {
			e.printStackTrace();
		}
    }
    
    private boolean enabled() {
    	return APPManagerConfigurationServiceComponent.getApiMgtConfigReaderService().isEnabled();
    }	
    
    private void loadPublisherClass() {
    	if (publisher == null) {
            synchronized (this){
                if (publisher == null) {
                	// (추가)
                	String publisherClass = APPManagerConfigurationServiceComponent.getApiMgtConfigReaderService().getPublisherClass();
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
