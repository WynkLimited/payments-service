package in.wynk.payment.presentation.dto.charge.netbanking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NonSeamlessNetBankingPaymentChargingResponse extends NetBankingPaymentChargingResponse {
    @JsonProperty("info")
    private String form;
}