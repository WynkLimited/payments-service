package in.wynk.payment.dto.gateway.netbanking;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */

@Getter
@ToString
@SuperBuilder
@Payment(groupId = "NET_BANKING", mode = "HTML")
public class NetBankingHtmlTypeResponse extends AbstractCoreNetBankingChargingResponse{
    @JsonProperty("info")
    private String html;
}
