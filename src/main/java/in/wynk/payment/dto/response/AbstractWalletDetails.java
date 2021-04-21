package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class AbstractWalletDetails extends AbstractPaymentDetails {
    private String linkedMobileNo;
}
