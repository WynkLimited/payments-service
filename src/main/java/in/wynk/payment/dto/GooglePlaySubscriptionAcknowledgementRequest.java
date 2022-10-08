package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.gpbs.request.GooglePlayAppDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayPaymentDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayProductDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class GooglePlaySubscriptionAcknowledgementRequest extends AbstractPaymentAcknowledgementRequest{
    @Analysed
    private GooglePlayAppDetails appDetails;

    @Analysed
    private GooglePlayPaymentDetails paymentDetails;

    @Analysed
    private GooglePlayProductDetails productDetails;

    @Analysed
    private String developerPayload;
}
