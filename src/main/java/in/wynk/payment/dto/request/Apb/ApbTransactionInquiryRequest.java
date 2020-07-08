package in.wynk.payment.dto.request.Apb;

import lombok.*;

@Builder
@ToString
@Getter
@AllArgsConstructor
@RequiredArgsConstructor
public class ApbTransactionInquiryRequest {
    private String txnRefNo;
    private String feSessionId;
    private String txnDate;
    private String merchantId;
    private String hash;
    private String amount;
    private String request;
    private String langId;
}
