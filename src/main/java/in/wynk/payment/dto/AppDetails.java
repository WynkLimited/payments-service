package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.core.dao.entity.App;
import in.wynk.wynkservice.core.dao.entity.Os;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Builder
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AppDetails implements IAppDetails, Serializable {

    private int buildNo;

    private String os;
    private String appId;
    private String service;
    private String deviceId;
    private String deviceType;
    private String appVersion;

    public WynkService getServiceObj() {
        return WynkServiceUtils.fromServiceId(service);
    }

    public App getAppObj() {
        return WynkServiceUtils.getServiceSupportedApp(appId, getServiceObj());
    }

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
