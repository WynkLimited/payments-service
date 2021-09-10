package in.wynk.payment.dto.response.payu;

import lombok.Getter;

@Getter
public class PayUPreDebitNotificationResponse extends PayUBaseResponse {
    private String amount;
    private String invoiceId;
    private String approvedStatus;
    private String invoiceStatus;
}