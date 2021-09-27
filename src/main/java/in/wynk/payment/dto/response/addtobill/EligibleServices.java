package in.wynk.payment.dto.response.addtobill;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class EligibleServices {
    boolean isEligible;
    private String serviceId;
    private List<LinkedSis> linkedSis;
    private List<String> paymentOptions;

}