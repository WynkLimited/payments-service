package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class UserMappingRequest {

    private String wynkUserId;
    private String externalUserId;
    private String msisdn;
    private PaymentCode code;
}
