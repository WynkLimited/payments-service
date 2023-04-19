package in.wynk.payment.presentation.dto.charge.netbanking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.queue.dto.Payment;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Payment(groupId = "NET_BANKING", mode = "html")
public class HtmlTypeNonSeamlessNetBankingPaymentChargingResponse extends NonSeamlessNetBankingPaymentChargingResponse {
    @JsonProperty("info")
    private String html;
}
