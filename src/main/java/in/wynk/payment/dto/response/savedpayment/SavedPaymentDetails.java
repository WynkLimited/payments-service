package in.wynk.payment.dto.response.savedpayment;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.SavedDetails;
import in.wynk.payment.dto.response.paymentoption.PaymentOptionsDTO;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@SuperBuilder
@Getter
@AnalysedEntity
public class SavedPaymentDetails extends PaymentOptionsDTO {
    private List<SavedDetails> savedDetails;
}
