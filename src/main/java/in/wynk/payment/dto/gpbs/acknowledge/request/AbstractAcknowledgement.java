package in.wynk.payment.dto.gpbs.acknowledge.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.gpbs.request.GooglePlayAppDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayPaymentDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayProductDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractAcknowledgement extends AbstractPaymentAcknowledgementRequest {
    @Analysed
    private GooglePlayAppDetails appDetails;

    @Analysed
    private GooglePlayPaymentDetails paymentDetails;

    @Analysed
    private GooglePlayProductDetails productDetails;

    @Analysed
    private String developerPayload;

    @Analysed
    private final String paymentCode;

    @Analysed
    private final String txnId;

    public abstract String getType ();

    @Override
    public String getTxnId () {
        return this.txnId;
    }
}
