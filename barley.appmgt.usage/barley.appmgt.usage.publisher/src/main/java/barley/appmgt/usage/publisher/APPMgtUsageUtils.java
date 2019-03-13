package barley.appmgt.usage.publisher;

import java.util.Collection;

import org.apache.synapse.MessageContext;
import org.apache.synapse.rest.API;

public class APPMgtUsageUtils {

    public static String getAppNameFromSynapseEnvironment(MessageContext synCtx, String referer) {

        // TODO check this logic when multitenancy is involved
        Collection<API> apis = synCtx.getEnvironment().getSynapseConfiguration().getAPIs();
        for (API api : apis) {
            if ("/".equals(api.getContext())) {
                return "/";
            } else if (referer.contains(api.getContext())) {
                return api.getAPIName();
            }
        }
        return null;
    }
}
