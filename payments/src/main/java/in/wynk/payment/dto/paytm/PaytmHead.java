package in.wynk.payment.dto.paytm;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class PaytmHead {

    private String version;
    private String clientId;
    private String channelId;
    private String signature;

}
