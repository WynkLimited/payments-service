package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

@Getter
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "action",
        defaultImpl = PayUChargingTransactionDetails.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PayURefundTransactionDetails.class, name = "refund")
})
public abstract class AbstractPayUTransactionDetails {

    @JsonProperty("mode")
    private String mode;

    @JsonProperty("status")
    private String status;

    @JsonProperty("mihpayid")
    private String payUExternalTxnId;

    @JsonProperty("amt")
    private double amount;

    @JsonProperty("bank_ref_num")
    private String bankReferenceNum;

}
