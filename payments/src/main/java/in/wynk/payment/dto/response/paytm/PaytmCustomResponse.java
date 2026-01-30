package in.wynk.payment.dto.response.paytm;

import in.wynk.common.enums.Status;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public abstract class PaytmCustomResponse {

    private Status status;
    private String message;
    private String responseCode;

}
