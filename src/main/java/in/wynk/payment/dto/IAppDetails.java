package in.wynk.payment.dto;

import in.wynk.wynkservice.core.dao.entity.App;
import in.wynk.wynkservice.core.dao.entity.Os;
import in.wynk.wynkservice.core.dao.entity.WynkService;

public interface IAppDetails {
    String getBuildNo();
    String getDeviceId();
    String getDeviceType();
    Os getOs();
    App getApp();
    WynkService getService();
}
