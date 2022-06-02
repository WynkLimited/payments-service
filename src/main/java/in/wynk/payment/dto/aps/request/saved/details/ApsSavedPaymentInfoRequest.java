package in.wynk.payment.dto.aps.request.saved.details;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
*  This service is used to fetch the all the saved-payment options of an userincluding (Debit Cards, CreditCards, Wallets, VPA Details etc.)
*/
@Getter
@Builder
@ToString
public class ApsSavedPaymentInfoRequest {
    private String loginId;
    private String siNumber;
    private String lob;
    private String subLob;
    private String circleId;
    private String appOs;
    private String appVersion;
}
