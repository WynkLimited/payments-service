package in.wynk.payment.dto.request.Apb;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Builder
@ToString
@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class ApbTransactionInquiryRequest {
    private String txnRefNO;
    private String feSessionId;
    private String txnDate;
    private String merchantId;
    private String hash;
    private String amount;
    private String request;
    private String langId;
}
