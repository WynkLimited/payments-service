package in.wynk.payment.dto.aps.common;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.Objects;

@Getter
@Builder
@ToString
public class UpiPaymentInfo<T extends UpiPaymentInfo.AbstractUpiDetails> extends AbstractPaymentInfo {

    /**
     * UpiDetails details
     */
    private T upiDetails;

    @JsonProperty("upiFlow")
    public String getUpiFlow() {
        if (Objects.isNull(upiDetails)) throw new IllegalArgumentException();
        return upiDetails.getUpiFlow();
    }

    public interface AbstractUpiDetails {
        @JsonIgnore
        String getUpiFlow();
    }

    @Getter
    @Builder
    @ToString
    public static class UpiIntentDetails implements AbstractUpiDetails {
        private String upiApp;
        /**
         * Static Value - INTENT_S2S
         */
        private final String upiFlow = "INTENT_S2S";
    }

    @Getter
    @Builder
    @ToString
    public static class UpiCollectDetails implements AbstractUpiDetails {
        private String vpa;
        /**
         * Static Value - COLLECT_S2S
         */
        private final String upiFlow = "INTENT_CUSTOM";
    }

}
