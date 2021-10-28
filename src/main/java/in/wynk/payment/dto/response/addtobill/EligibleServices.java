package in.wynk.payment.dto.response.addtobill;

import lombok.*;

import java.util.List;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EligibleServices {
    private boolean isEligible;
    private String serviceId;
    private List<LinkedSis> linkedSis;
    private List<String> paymentOptions;
    private AddToBillPricingDetails pricingDetails;
}