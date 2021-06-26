package in.wynk.payment.dto;

import in.wynk.wynkservice.core.dao.entity.App;
import in.wynk.wynkservice.core.dao.entity.Os;
import in.wynk.wynkservice.core.dao.entity.WynkService;

public interface IAppDetails {
    int getBuildNo();
    String getDeviceId();
    String getDeviceType();
    String getAppVersion();
    Os getOs();
    App getApp();
    WynkService getService();
}
