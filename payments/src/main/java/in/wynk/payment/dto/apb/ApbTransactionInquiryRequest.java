package in.wynk.payment.dto.apb;

import lombok.*;

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
