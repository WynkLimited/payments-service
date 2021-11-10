package in.wynk.payment.dto.response.addtobill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class EligibilityDetails {
    private boolean isIsEligible;
    private String ineligibleToastMessage;

    public boolean isIsEligible() {
        return isIsEligible;
    }

    public void setIsEligible(boolean isEligible) {
        isIsEligible = isEligible;
    }
}
