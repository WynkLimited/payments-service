package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.core.dao.entity.App;
import in.wynk.wynkservice.core.dao.entity.Os;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import lombok.*;

import java.io.Serializable;

@Getter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
public class AppDetails implements IAppDetails, Serializable {

    private int buildNo;

    private String os;
    private String appId;
    private String service;
    private String deviceId;
    private String deviceType;
    private String appVersion;

    @JsonIgnore
    public WynkService getServiceObj() {
        return WynkServiceUtils.fromServiceId(service);
    }

    @JsonIgnore
    public App getAppObj() {
        return WynkServiceUtils.getServiceSupportedApp(appId, getServiceObj());
    }

    @JsonIgnore
    public Os getOsObj() {
        return WynkServiceUtils.getAppSupportedOs(os, getAppObj());
    }

    public String getService() {
        return getServiceObj().getId();
    }

    public String getOs() {
        return getOsObj().getId();
    }

    @Override
    public String getAppId() {
        return getAppObj().getId();
    }
}
