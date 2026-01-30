package in.wynk.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AppDetailsDto {
    private int buildNo;
    private String deviceType;
    private String os;
    private String appId;
    private String service;
    private String appVersion;
    private String deviceId;

}
