package in.wynk.payment.eligibility.request;

import in.wynk.eligibility.dto.IEligibilityRequest;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang.StringUtils;

import java.util.Objects;

@Getter
@SuperBuilder
public abstract class PaymentOptionsEligibilityRequest implements IEligibilityRequest {
    private final String service;
    private final String uid;
    @Setter
    private String group;

    private final String msisdn;
    private final String countryCode;
    private final String couponCode;
    private final String appId;
    private final int buildNo;
    private final String os;


    public static PaymentOptionsEligibilityRequest from(PaymentOptionsComputationDTO computationDTO) {
        final PlanDTO planDTO = computationDTO.getPlanDTO();
        final ItemDTO itemDTO = computationDTO.getItemDTO();
        if (Objects.nonNull(planDTO)) {
            PaymentOptionsPlanEligibilityRequest.PaymentOptionsPlanEligibilityRequestBuilder builder = PaymentOptionsPlanEligibilityRequest.builder();
            builder.planId(String.valueOf(planDTO.getId())).appId(computationDTO.getAppId()).buildNo(computationDTO.getBuildNo()).countryCode(computationDTO.getCountryCode()).couponCode(computationDTO.getCouponCode()).service(planDTO.getService());
            if (StringUtils.isNotEmpty(computationDTO.getMsisdn())) {
                builder.msisdn(computationDTO.getMsisdn());
            }
            return builder.build();
        } else {
            PaymentOptionsItemEligibilityRequest.PaymentOptionsItemEligibilityRequestBuilder builder = PaymentOptionsItemEligibilityRequest.builder();
            builder.itemId(String.valueOf(itemDTO.getId())).appId(computationDTO.getAppId()).buildNo(computationDTO.getBuildNo()).countryCode(computationDTO.getCountryCode()).couponCode(computationDTO.getCouponCode()).service(itemDTO.getService());
            if (StringUtils.isNotEmpty(computationDTO.getMsisdn())) {
                builder.msisdn(computationDTO.getMsisdn());
            }
            return builder.build();
        }
    }
}
