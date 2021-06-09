package in.wynk.payment.dto.apb.paytm;
import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.response.Apb.paytm.APBPaytmResponseData;
import lombok.*;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class APBPaytmTopUpRequest{
    private String channel;
    private String encryptedToken;
    private String authType;
    private APBTopUpInfo topUpInfo;
    private APBPaytmUserInfo userInfo;

}