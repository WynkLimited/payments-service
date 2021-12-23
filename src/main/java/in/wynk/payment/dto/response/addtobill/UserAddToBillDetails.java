package in.wynk.payment.dto.response.addtobill;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import in.wynk.vas.client.dto.atb.LinkedSis;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

@Getter
@SuperBuilder
@AnalysedEntity
public class UserAddToBillDetails extends AbstractPaymentDetails {
    private final List<LinkedSis> linkedSis;
    private final double amount;
}





