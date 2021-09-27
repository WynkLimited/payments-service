package in.wynk.payment.dto.addtobill;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Getter
@Builder
public class AddToBillEligibilityAndPricingRequest {

        private String channel = "NEW_PARTNER_CHANNEL";
        private String pageIdentifier = "DETAILS";
        private Set<String> serviceIds;
        private Map serviceMeta;
        private String si = "9876543210";
        private String skuGroupId;

}
