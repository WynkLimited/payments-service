package in.wynk.payment.dto.gpbs.acknowledge.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractPaymentAcknowledgementRequest {
    public abstract String getPaymentCode ();
    public abstract String getType ();
    public abstract String getTxnId ();
}
