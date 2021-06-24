package in.wynk.payment.dto.apb.paytm;
import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.response.Apb.paytm.APBPaytmResponseData;
import lombok.*;
import lombok.experimental.SuperBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder
@Getter
public class APBPaytmTopUpRequest extends APBPaytmAuthAndChannel{
    private String orderId;
    private String encryptedToken;
    private APBTopUpInfo topUpInfo;
    private APBPaytmUserInfo userInfo;


}