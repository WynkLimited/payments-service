package in.wynk.payment.dto.aps.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder
public class IntentUpiPaymentInfo extends AbstractUpiPaymentInfo {

    private final String upiFlow = "INTENT_S2S";
    private UpiDetails upiDetails;

    @Builder
    @Getter
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class UpiDetails {
        private String appName;
    }

}
