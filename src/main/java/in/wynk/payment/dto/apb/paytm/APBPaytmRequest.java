package in.wynk.payment.dto.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder
@Getter
@AllArgsConstructor
public class APBPaytmRequest {
    private String walletLoginId;
    //private String loginId;
    private String wallet;
    private String authType;
    private String channel;
    private String encryptedToken;
}
