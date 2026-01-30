package in.wynk.payment.presentation.dto.charge.netbanking;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeamlessNetBankingPaymentChargingResponse extends NetBankingPaymentChargingResponse{
}