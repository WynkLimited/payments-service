package in.wynk.payment.dto.response.billing;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.SavedDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class SavedBillingPayment extends SavedDetails {
    private String linkedSis;
    private Double balance;
}
