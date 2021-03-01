package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class PayURefundTransactionDetails extends AbstractPayUTransactionDetails {

    @JsonProperty("request_id")
    private String requestId;

    @JsonProperty("action")
    private String action;

    @JsonProperty("token")
    private String token;

    @JsonProperty("bank_arn")
    private String bankArn;

    @JsonProperty("settlement_id")
    private String settlementId;

    @JsonProperty("amount_settled")
    private double amountSettled;

    @JsonProperty("UTR_no")
    private String utrNumber;

    @JsonProperty("value_date")
    private String valueDate;

}
