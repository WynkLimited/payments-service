package in.wynk.payment.dto.gateway.netbanking;

import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "NET_BANKING", mode = "KEY_VALUE")
public class NetBankingKeyValueTypeResponse extends AbstractCoreNetBankingChargingResponse {
    private Map<String, String> form;

    private String url;
}