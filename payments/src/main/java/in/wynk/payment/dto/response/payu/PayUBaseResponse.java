package in.wynk.payment.dto.response.payu;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class PayUBaseResponse {
    private String status;
    private String action;
    private String message;
}