package in.wynk.payment.eligibility.request;

import in.wynk.eligibility.dto.IEligibilityRequest;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang.StringUtils;

import java.util.Objects;

@Getter
@SuperBuilder
public abstract class PaymentOptionsEligibilityRequest implements IEligibilityRequest {

    private final String os;
    private final String uid;
    private final String appId;
    private final String msisdn;
    private final String service;
    private final String couponCode;
    private final String countryCode;
    private final int buildNo;
    @Setter
    private String group;

    public static PaymentOptionsEligibilityRequest from(PaymentOptionsComputationDTO computationDTO) {
        final PlanDTO planDTO = computationDTO.getPlanDTO();
        final ItemDTO itemDTO = computationDTO.getItemDTO();
        if (Objects.nonNull(planDTO)) {
            PaymentOptionsPlanEligibilityRequest.PaymentOptionsPlanEligibilityRequestBuilder builder = PaymentOptionsPlanEligibilityRequest.builder();
            builder.planId(String.valueOf(planDTO.getId())).appId(computationDTO.getAppId()).buildNo(computationDTO.getBuildNo()).countryCode(computationDTO.getCountryCode()).couponCode(computationDTO.getCouponCode()).service(planDTO.getService()).os(computationDTO.getOs());
            if (StringUtils.isNotEmpty(computationDTO.getMsisdn())) builder.msisdn(computationDTO.getMsisdn());
            if (computationDTO.getCountryCode() == null) {
                builder.countryCode(WynkServiceUtils.fromServiceId(planDTO.getService()).getDefaultCountryCode());
            }
            return builder.planId(String.valueOf(planDTO.getId())).build();
        } else {
            PaymentOptionsItemEligibilityRequest.PaymentOptionsItemEligibilityRequestBuilder builder = PaymentOptionsItemEligibilityRequest.builder();
            builder.itemId(String.valueOf(itemDTO.getId())).appId(computationDTO.getAppId()).buildNo(computationDTO.getBuildNo()).countryCode(computationDTO.getCountryCode()).couponCode(computationDTO.getCouponCode()).service(itemDTO.getService()).os(computationDTO.getOs());
            if (StringUtils.isNotEmpty(computationDTO.getMsisdn())) builder.msisdn(computationDTO.getMsisdn());
            if (computationDTO.getCountryCode() == null) {
                builder.countryCode(WynkServiceUtils.fromServiceId(itemDTO.getService()).getDefaultCountryCode());
            }
            return builder.itemId(itemDTO.getId()).build();
        }
    }
}