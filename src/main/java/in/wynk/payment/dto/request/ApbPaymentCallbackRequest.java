package in.wynk.payment.dto.request;

import in.wynk.payment.enums.Apb.ApbStatus;
import in.wynk.payment.enums.Apb.Currency;
import lombok.Data;

@Data
public class ApbPaymentCallbackRequest {
    private ApbStatus status;
    private String code;
    private String msg;
    private String merchantId;
    private String externalTxnId;
    private String amount;
    private Currency currency;
    private String txnDate;
    private String txnId;
    private String hash;
}
