package in.wynk.payment.dto.apb.paytm;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APBPaytmLinkRequest{
    private String walletLoginId;
    private String loginId;
    private String wallet;
    private String authType;
}
