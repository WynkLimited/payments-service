package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
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
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
public class AppDetails implements IAppDetails, Serializable {

    @Analysed
    private int buildNo;
    @Analysed
    private String os;
    @Analysed
    private String appId;
    @Analysed
    private String service;
    @Analysed
    private String deviceId;
    @Analysed
    private String deviceType;
    @Analysed
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
