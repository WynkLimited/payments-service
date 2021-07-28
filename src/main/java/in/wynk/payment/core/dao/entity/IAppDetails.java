package in.wynk.payment.core.dao.entity;

public interface IAppDetails {
    int getBuildNo();
    String getDeviceId();
    String getDeviceType();
    String getAppVersion();
    String getOs();
    String getAppId();
    String getService();
}
