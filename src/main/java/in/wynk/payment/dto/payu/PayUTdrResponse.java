package in.wynk.payment.dto.payu;

import lombok.Getter;

@Getter

public class PayUTdrResponse {
     private String message;
     private boolean status;

    public PayUTdDetails getTdr() {
        PayUTdDetails tdr = new PayUTdDetails();
        return tdr;
    }

}


