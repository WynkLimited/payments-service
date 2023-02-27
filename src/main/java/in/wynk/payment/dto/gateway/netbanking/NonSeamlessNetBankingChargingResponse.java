package in.wynk.payment.dto.gateway.netbanking;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Getter
@ToString
@SuperBuilder
public class NonSeamlessNetBankingChargingResponse extends AbstractCoreNetBankingChargingResponse {
    private Map<String, String> form;
}