package in.wynk.payment.core.dao.entity;

import java.io.Serializable;

public interface IAppDetails extends Serializable {

    int getBuildNo();

    String getOs();

    String getAppId();

    String getService();

    String getDeviceId();

    String getDeviceType();

    String getAppVersion();

}