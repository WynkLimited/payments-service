package in.wynk.payment.dto.payu;

import lombok.Getter;

@Getter
public class PayUTdrResponse {
    private PayUTdDetails message;
    private boolean status;

    @Getter
    public static class PayUTdDetails {
        private double tdr;
    }

}


