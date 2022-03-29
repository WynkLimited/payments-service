package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentDetails extends AbstractPaymentDetails {

}