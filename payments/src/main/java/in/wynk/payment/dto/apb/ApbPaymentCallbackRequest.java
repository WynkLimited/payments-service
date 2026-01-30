package in.wynk.payment.dto.apb;

import in.wynk.common.enums.Currency;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@AllArgsConstructor
@RequiredArgsConstructor
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
