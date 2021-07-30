package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaytmResponse<T> {

    private PaytmResponseHead head;
    private T body;

}