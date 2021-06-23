package in.wynk.payment.core.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ChargingType {

    WEB_PLAN("WEB_PLAN"),  WEB_POINT("WEB_POINT"), S2S_PLAN("S2S_PLAN"), S2S_POINT("S2S_POINT");

    private final String type;

}
