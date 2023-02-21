package in.wynk.payment.presentation;

import in.wynk.common.dto.IWynkPresentation;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.common.response.AbstractVerificationResponse;
import in.wynk.payment.presentation.dto.DefaultVerifyUserPaymentResponse;
import in.wynk.payment.presentation.dto.VerifyUserPaymentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class VerifyUserPaymentPresentation implements IWynkPresentation<WynkResponseEntity<VerifyUserPaymentResponse>, AbstractVerificationResponse> {

    @Override
    public WynkResponseEntity<VerifyUserPaymentResponse> transform(AbstractVerificationResponse payload) {
        VerifyUserPaymentResponse response = DefaultVerifyUserPaymentResponse.builder().valid(payload.isValid())
                .autoRenewSupported(payload.isAutoRenewSupported())
                .verificationType(payload.getVerificationType()).build();
        switch (payload.getVerificationType()) {
            case VPA:
                return WynkResponseEntity.<VerifyUserPaymentResponse>builder().data(response).status(HttpStatus.OK).build();
            case BIN:
                return WynkResponseEntity.<VerifyUserPaymentResponse>builder().data(response).status(response.isValid() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).build();
            default:
                return WynkResponseEntity.<VerifyUserPaymentResponse>builder().build();
        }
    }
}
