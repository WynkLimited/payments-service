package in.wynk.payment.dto.response.payu;

import lombok.Getter;

@Getter
public class PayUBaseResponse {
    private int status;
    private String action;
    private String message;
}