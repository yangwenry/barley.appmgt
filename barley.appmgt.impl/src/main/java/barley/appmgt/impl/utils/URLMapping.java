package barley.appmgt.impl.utils;

public class URLMapping {
	private String urlPattern;
	private String authScheme;
	private String httpMethod;
	private String throttlingTier;
    private String userRoles;

    public String getUserRoles() { return  userRoles; }

    public void setUserRoles(String userRoles) { this.userRoles = userRoles; }

	public String getUrlPattern() {
		return urlPattern;
	}

	public void setUrlPattern(String urlPattern) {
		this.urlPattern = urlPattern;
	}

	public String getAuthScheme() {
		return authScheme;
	}

	public void setAuthScheme(String authScheme) {
		this.authScheme = authScheme;
	}

	public String getHttpMethod() {
		return httpMethod;
	}

	public void setHttpMethod(String httpMethod) {
		this.httpMethod = httpMethod;
	}

	public String getThrottlingTier() {
		return throttlingTier;
	}

	public void setThrottlingTier(String throttlingTier) {
		this.throttlingTier = throttlingTier;
	}

}
