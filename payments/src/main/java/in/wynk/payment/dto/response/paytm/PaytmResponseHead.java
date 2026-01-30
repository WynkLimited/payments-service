package in.wynk.payment.dto.response.paytm;

import in.wynk.payment.dto.paytm.PaytmHead;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class PaytmResponseHead extends PaytmHead {
    private String responseTimestamp;
}
