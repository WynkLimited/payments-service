package in.wynk.payment.dto.gpbs.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.core.dao.entity.PaymentCode;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.IapVerificationRequestV2;
import lombok.Getter;
import lombok.Setter;

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
    private GooglePlayUserDetails userDetails;

    @Analysed
    private GooglePlayProductDetails productDetails;

    @Override
    public PaymentCode getPaymentCode () {
        return PaymentCodeCachingService.getFromPaymentCode(GOOGLE_IAP);
    }
}
