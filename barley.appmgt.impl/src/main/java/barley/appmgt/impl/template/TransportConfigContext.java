package barley.appmgt.impl.template;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.axis2.Constants;
import org.apache.velocity.VelocityContext;

import barley.appmgt.api.AppManagementException;
import barley.appmgt.api.model.WebApp;

/**
 * Set the transport config context
 */
public class TransportConfigContext extends ConfigContextDecorator {

    private WebApp api;

    public TransportConfigContext(ConfigContext context, WebApp api) {
        super(context);
        this.api = api;
    }

    @Override
    public void validate() throws APITemplateException, AppManagementException {
        super.validate();
    }

    @Override
    public VelocityContext getContext() {
        VelocityContext context = super.getContext();
        if (api.getTransports().contains(",")) {
            List<String> transports = new ArrayList<String>(Arrays.asList(api.getTransports().split(",")));
            if(transports.contains(Constants.TRANSPORT_HTTP) && transports.contains(Constants.TRANSPORT_HTTPS)){
                context.put("transport","");
            }
        }else{
            context.put("transport",api.getTransports());
        }

        return context;
    }
}
