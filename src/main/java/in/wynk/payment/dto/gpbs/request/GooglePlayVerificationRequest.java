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
    @Valid
    private GooglePlayPaymentDetails paymentDetails;

    @Analysed
    @Valid
    private GooglePlayUserDetails userDetails;

    @Analysed
    @Valid
    private GooglePlayProductDetails productDetails;

    @Override
    public PaymentCode getPaymentCode () {
        return PaymentCodeCachingService.getFromPaymentCode(GOOGLE_IAP);
    }

    @Override
    public void setGooglePlayPaymentDetails (GooglePlayPaymentDetails googlePlayPaymentDetails) {
        this.paymentDetails = googlePlayPaymentDetails;
    }

    @Override
    public void setGooglePlayAppDetails (GooglePlayAppDetails googlePlayAppDetails) {
        this.appDetails = googlePlayAppDetails;
    }

    @Override
    public void setGooglePlayProductDetails (GooglePlayProductDetails googlePlayProductDetails) {
        this.productDetails = googlePlayProductDetails;
    }
}
