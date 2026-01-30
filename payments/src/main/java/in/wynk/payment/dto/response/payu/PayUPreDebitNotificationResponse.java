package in.wynk.payment.dto.response.payu;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class PayUPreDebitNotificationResponse extends PayUBaseResponse {
    private String amount;
    private String invoiceId;
    private String approvedStatus;
    private String invoiceStatus;
}