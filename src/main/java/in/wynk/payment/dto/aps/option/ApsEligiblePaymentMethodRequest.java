package in.wynk.payment.dto.aps.option;

import lombok.Getter;
import lombok.ToString;

/**
*  This service is used to fetch the all the eligible payment-options for a LOB.
*/

@Getter
@ToString
public class ApsEligiblePaymentMethodRequest {
    private String loginId;
    private String siNumber;
    private String lob;
    private String subLob;
    private String circleId;
    private String appOs;
    private String appVersion;
    private boolean cohortEnabled;
    private String cohortValue;
}
