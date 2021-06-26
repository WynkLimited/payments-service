package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
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
public class PayerDetails implements IPayerDetails, Serializable {

    private String os;
    private String appId;
    private String msisdn;
    private String service;
    private String buildNo;
    private String deviceId;
    private String deviceType;
    private String subscriberId;

    public WynkService getService() {
        return WynkServiceUtils.fromServiceId(service);
    }

    public App getApp() {
        return WynkServiceUtils.getServiceSupportedApp(appId, getService());
    }

    public Os getOs() {
        return WynkServiceUtils.getAppSupportedOs(os, getApp());
    }

}
