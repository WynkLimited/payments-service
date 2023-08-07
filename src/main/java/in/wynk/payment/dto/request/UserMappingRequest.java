package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.GeoLocation;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;

import static in.wynk.common.constant.CacheBeanNameConstants.INVALID_VALUE;
import static in.wynk.common.constant.CacheBeanNameConstants.MSISDN_REGEX;

@Getter
@Builder
@AllArgsConstructor
@AnalysedEntity
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

    public PaymentGateway getCode() {
        return PaymentCodeCachingService.getFromPaymentCode(this.code);
    }

}