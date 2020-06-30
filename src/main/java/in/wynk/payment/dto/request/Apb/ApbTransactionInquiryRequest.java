package in.wynk.payment.dto.request.Apb;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.ToString;

@Builder
@ToString
public class ApbTransactionInquiryRequest {
    @JsonProperty("txnRefNO")
    private String txnRefNo;
    private String feSessionId;
    private String txnDate;
    private String merchantId;
    private String hash;
    private String amount;
    private String request;
    private String langId;
}
