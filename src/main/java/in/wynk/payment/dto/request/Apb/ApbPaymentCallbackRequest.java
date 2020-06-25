package in.wynk.payment.dto.request.Apb;

import in.wynk.commons.enums.Currency;
import in.wynk.payment.enums.Apb.ApbStatus;
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
