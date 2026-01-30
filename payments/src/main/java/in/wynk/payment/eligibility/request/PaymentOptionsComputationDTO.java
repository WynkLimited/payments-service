package in.wynk.payment.eligibility.request;

import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentOptionsComputationDTO {
    private final PlanDTO planDTO;
    private final String msisdn;
    private final ItemDTO itemDTO;
    private final String group;
    private final String countryCode;
    private final String couponCode;
    private final String appId;
    private final int buildNo;
    private final String os;
    private final String si;
    private final String client;
}
