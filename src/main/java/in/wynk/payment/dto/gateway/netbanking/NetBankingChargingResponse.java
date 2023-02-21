package in.wynk.payment.dto.gateway.netbanking;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class NetBankingChargingResponse extends AbstractCoreNetBankingChargingResponse{
    private String html;
}
