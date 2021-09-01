package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.*;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import static in.wynk.common.constant.CacheBeanNameConstants.INVALID_VALUE;
import static in.wynk.common.constant.CacheBeanNameConstants.MSISDN_REGEX;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserMappingRequest {

    @NotNull
    @Pattern(regexp = MSISDN_REGEX, message = INVALID_VALUE)
    private String msisdn;

    @NotNull
    private String wynkUserId;

    @NotNull
    private String externalUserId;

    @NotNull
    private PaymentCode code;

}