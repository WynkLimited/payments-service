package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
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
