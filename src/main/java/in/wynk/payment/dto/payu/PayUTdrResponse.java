package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class PayUTdrResponse {
    @JsonProperty("TDR_details")
    private PayUTdDetails message;
    private boolean status;

    @Getter
    public static class PayUTdDetails {
        @JsonProperty("TDR")
        private double tdr;
    }

}


