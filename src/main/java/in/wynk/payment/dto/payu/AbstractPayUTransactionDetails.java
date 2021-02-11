package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.NONE)
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
