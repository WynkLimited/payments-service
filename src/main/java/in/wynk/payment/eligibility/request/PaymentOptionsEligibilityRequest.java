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
    private final String msisdn;
    private final String service;
    private final String uid;
    @Setter
    private String group;

    public static PaymentOptionsEligibilityRequest from(PaymentOptionsComputationDTO computationDTO) {
        final PlanDTO planDTO = computationDTO.getPlanDTO();
        final ItemDTO itemDTO = computationDTO.getItemDTO();
        if (Objects.nonNull(planDTO)) {
            PaymentOptionsPlanEligibilityRequest.PaymentOptionsPlanEligibilityRequestBuilder builder = PaymentOptionsPlanEligibilityRequest.builder();
            builder.service(planDTO.getService());
            builder.planId(String.valueOf(planDTO.getId()));
            if (StringUtils.isNotEmpty(computationDTO.getMsisdn())) {
                builder.msisdn(computationDTO.getMsisdn());
            }
            return builder.build();
        } else {
            PaymentOptionsItemEligibilityRequest.PaymentOptionsItemEligibilityRequestBuilder builder = PaymentOptionsItemEligibilityRequest.builder();
            builder.service(itemDTO.getService());
            builder.itemId(String.valueOf(itemDTO.getId()));
            if (StringUtils.isNotEmpty(computationDTO.getMsisdn())) {
                builder.msisdn(computationDTO.getMsisdn());
            }
            return builder.build();
        }
    }
}
