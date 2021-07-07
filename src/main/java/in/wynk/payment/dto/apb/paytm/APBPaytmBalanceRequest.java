package in.wynk.payment.dto.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@SuperBuilder
public class APBPaytmBalanceRequest extends APBPaytmRequest{
    private String encryptedToken;
}
