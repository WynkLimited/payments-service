package in.wynk.payment.dto;

import in.wynk.wynkservice.core.dao.entity.App;
import in.wynk.wynkservice.core.dao.entity.Os;
import in.wynk.wynkservice.core.dao.entity.WynkService;

public interface IPayerDetails {
    String getMsisdn();
    String getBuildNo();
    String getDeviceId();
    String getDeviceType();
    String getSubscriberId();

    Os getOs();
    App getApp();
    WynkService getService();
}
