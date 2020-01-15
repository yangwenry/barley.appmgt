package barley.appmgt.impl.utils;

public class URLMapping {
	private String urlPattern;
	private String authScheme;
	private String httpMethod;
	private String throttlingTier;
    private String userRoles;    
    // (추가)
    private int policyGroupId;
    private String policyGroupName;

    
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
	
	public int getPolicyGroupId() {
		return policyGroupId;
	}

	public void setPolicyGroupId(int policyGroupId) {
		this.policyGroupId = policyGroupId;
	}

	public String getPolicyGroupName() {
		return policyGroupName;
	}

	public void setPolicyGroupName(String policyGroupName) {
		this.policyGroupName = policyGroupName;
	}

}
