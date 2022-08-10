package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.core.dao.entity.PaymentCode;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.subscription.common.dto.GeoLocation;
import lombok.*;

import javax.validation.constraints.NotBlank;
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
    private String code;

    @NotNull
    @Pattern(regexp = MSISDN_REGEX, message = INVALID_VALUE)
    private String msisdn;

    @NotBlank
    private String wynkUserId;

    private String externalUserId;

    @Analysed
    private GeoLocation geoLocation;

    public PaymentCode getCode() {
        return PaymentCodeCachingService.getFromPaymentCode(this.code);
    }

}