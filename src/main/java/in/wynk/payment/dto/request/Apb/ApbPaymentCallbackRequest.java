package in.wynk.payment.dto.request.Apb;

import in.wynk.commons.enums.Currency;
import in.wynk.payment.enums.Apb.ApbStatus;
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
