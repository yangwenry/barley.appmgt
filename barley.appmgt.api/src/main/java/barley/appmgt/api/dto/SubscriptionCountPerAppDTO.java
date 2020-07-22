package barley.appmgt.api.dto;

public class SubscriptionCountPerAppDTO {
    private String providerName;
    private String appName;
    private String version;
    private long count;

    public String getProviderName() {
        return providerName;
    }

    public void setProviderName(String providerName) {
        this.providerName = providerName;
    }

    public String getAppName() {
        return appName;
    }

    public void setAppName(String appName) {
        this.appName = appName;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    @Override
    public String toString() {
        return "SubscriptionCountPerAppDTO{" +
                "providerName='" + providerName + '\'' +
                ", appName='" + appName + '\'' +
                ", version='" + version + '\'' +
                ", count=" + count +
                '}';
    }
}
