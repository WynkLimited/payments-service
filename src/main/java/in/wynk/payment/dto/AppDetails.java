package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.core.dao.entity.App;
import in.wynk.wynkservice.core.dao.entity.Os;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Objects;

import static in.wynk.common.constant.CacheBeanNameConstants.*;

@Getter
@Setter
@SuperBuilder
@ToString
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
public class AppDetails implements IAppDetails, Serializable {

    @Analysed
    private int buildNo;

    @Analysed
    private String deviceType;

    @NotNull
    @Analysed
    @MongoBaseEntityConstraint(beanName = OS)
    private String os;

    @NotNull
    @Analysed
    @MongoBaseEntityConstraint(beanName = APP)
    private String appId;

    @NotNull
    @Analysed
    @MongoBaseEntityConstraint(beanName = WYNK_SERVICE)
    private String service;

    @NotBlank
    @Analysed
    private String appVersion;

    @NotBlank
    @Analysed
    private String deviceId;

    @Analysed
    private String device;

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

    public String getDeviceId() {
        if(Objects.isNull(deviceId) && Objects.nonNull(device)){
            return device;
        }
        return deviceId;
    }

    public String getOs() {
        return getOsObj().getId();
    }

    @Override
    public String getAppId() {
        return getAppObj().getId();
    }

    public in.wynk.common.dto.AppDetails toAppDetails() {
        return in.wynk.common.dto.AppDetails.builder().deviceType(deviceType).deviceId(deviceId).buildNo(buildNo).service(service).appId(appId).appVersion(appVersion).os(os).build();
    }

}