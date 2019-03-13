package barley.appmgt.impl.template;

import org.apache.velocity.VelocityContext;

import barley.appmgt.api.model.WebApp;
import barley.appmgt.impl.AppMConstants;

/**
 * Set if response caching enabled or not
 */
public class ResponseCacheConfigContext extends ConfigContextDecorator {

	private WebApp api;

	public ResponseCacheConfigContext(ConfigContext context, WebApp api) {
		super(context);
		this.api = api;
	}

	public VelocityContext getContext() {
		VelocityContext context = super.getContext();

		if (AppMConstants.API_RESPONSE_CACHE_ENABLED.equalsIgnoreCase(api.getResponseCache())) {
			context.put("responseCacheEnabled", true);
			context.put("responseCacheTimeOut", api.getCacheTimeout());
		} else {
			context.put("responseCacheEnabled", false);
		}

		return context;
	}

}
