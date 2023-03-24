package in.wynk.payment.presentation;

import in.wynk.common.dto.IWynkPresentation;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.dto.gateway.verify.BinVerificationResponse;
import in.wynk.payment.dto.gateway.verify.VpaVerificationResponse;
import in.wynk.payment.presentation.dto.verify.BinVerifyUserPaymentResponse;
import in.wynk.payment.presentation.dto.verify.VpaVerifyUserPaymentResponse;
import in.wynk.payment.presentation.dto.verify.VerifyUserPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentMethodVerificationPresentation implements IWynkPresentation<WynkResponseEntity<VerifyUserPaymentResponse>, AbstractVerificationResponse> {

    @Override
    public WynkResponseEntity<VerifyUserPaymentResponse> transform(AbstractVerificationResponse payload) {

        switch (payload.getVerificationType()) {
            case VPA:
                VpaVerificationResponse vpaVerificationResponse = (VpaVerificationResponse) payload;
                VerifyUserPaymentResponse response = VpaVerifyUserPaymentResponse.builder().valid(vpaVerificationResponse.isValid())
                        .verificationType(vpaVerificationResponse.getVerificationType())
                        .autoPayVPAValid(vpaVerificationResponse.isAutoPayVPAValid())
                        .autoPayBankValid(vpaVerificationResponse.isAutoPayBankValid()).build();
                return WynkResponseEntity.<VerifyUserPaymentResponse>builder().data(response).build();
            case BIN:
                BinVerificationResponse binVerificationResponse = (BinVerificationResponse) payload;
                VerifyUserPaymentResponse verifyResponse = BinVerifyUserPaymentResponse.builder().valid(binVerificationResponse.isValid())
                        .autoRenewSupported(binVerificationResponse.isAutoRenewSupported())
                        .verificationType(binVerificationResponse.getVerificationType())
                        .inAppOtpSupport(binVerificationResponse.isInAppOtpSupport())
                        .cardCategory(binVerificationResponse.getCardCategory())
                        .cardType(binVerificationResponse.getCardType())
                        .isDomestic(binVerificationResponse.isDomestic()? "Y" : "N").build();
                return WynkResponseEntity.<VerifyUserPaymentResponse>builder().data(verifyResponse).build();
            default:
                return WynkResponseEntity.<VerifyUserPaymentResponse>builder().build();
        }
    }
}
