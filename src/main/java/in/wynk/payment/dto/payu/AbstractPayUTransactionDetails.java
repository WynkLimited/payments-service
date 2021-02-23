package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import lombok.Getter;

@Getter
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
@JsonSubTypes({@JsonSubTypes.Type(value = PayURefundTransactionDetails.class, name = PayUConstants.PAYU_REFUND_TRANSACTION_TYPE),
        @JsonSubTypes.Type(value = PayUChargingTransactionDetails.class, name = PayUConstants.PAYU_CHARGING_TRANSACTION_TYPE)})
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

    @JsonProperty("payu_transaction_types")
    public abstract String getType();

}
