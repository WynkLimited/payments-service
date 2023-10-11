package in.wynk.payment.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.event.common.AbstractWaOrderDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WaPayChargeRespEvent<T extends AbstractWaOrderDetails> extends AbstractWaPaymentEvent<T> {
    private String deeplink;
    private String payConfigId;
}
