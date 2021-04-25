package in.wynk.payment.dto.paytm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaytmRequestHead extends PaytmHead {
    private String requestTimestamp;
}
