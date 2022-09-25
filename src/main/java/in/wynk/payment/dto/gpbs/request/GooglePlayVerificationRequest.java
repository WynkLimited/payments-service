package in.wynk.payment.dto.gpbs.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.PaymentCode;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.IapVerificationRequestV2;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;

import static in.wynk.payment.core.constant.PaymentConstants.GOOGLE_IAP;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class GooglePlayVerificationRequest extends IapVerificationRequestV2 {

    @Valid
    @Analysed
    private GooglePlayAppDetails appDetails;

    @Analysed
    private GooglePlayPaymentDetails paymentDetails;

    @Analysed
    private GoogleUserDetails userDetails;

    @Override
    public PaymentCode getPaymentCode () {
        return PaymentCodeCachingService.getFromPaymentCode(GOOGLE_IAP);
    }
}
