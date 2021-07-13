package in.wynk.payment.dto.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.request.WalletValidateLinkRequest;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@SuperBuilder
public class APBPaytmLinkRequest extends APBPaytmRequest {

}
