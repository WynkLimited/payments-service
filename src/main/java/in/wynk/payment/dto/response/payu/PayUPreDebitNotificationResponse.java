package in.wynk.payment.dto.response.payu;

import lombok.Getter;

@Getter
public class PayUPreDebitNotificationResponse {

    private int status;
    private String amount;
    private String action;
    private String message;
    private String invoiceId;
    private String approvedStatus;
    private String invoiceStatus;

}