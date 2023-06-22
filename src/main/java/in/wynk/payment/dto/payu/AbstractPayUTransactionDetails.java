package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "action",
        defaultImpl = PayUChargingTransactionDetails.class)
@JsonSubTypes({
        @JsonSubTypes.Type(value = PayURefundTransactionDetails.class, name = "refund")
})
@NoArgsConstructor
@AllArgsConstructor
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

    public static <R extends AbstractPayUTransactionDetails, T extends PayUCallbackRequestPayload> R from(T payload) {
        return PayUAutoRefundCallbackRequestPayload.class.isAssignableFrom(payload.getClass()) ? (R) PayURefundTransactionDetails.from((PayUAutoRefundCallbackRequestPayload) payload) : (R) PayUChargingTransactionDetails.from(payload);
    }


}
