package in.wynk.payment.presentation;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.WynkResponseUtils;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.gateway.callback.DefaultPaymentCallbackResponse;
import in.wynk.payment.dto.manager.CallbackResponseWrapper;
import in.wynk.payment.presentation.dto.callback.DefaultCallbackResponse;
import in.wynk.payment.presentation.dto.callback.PaymentCallbackResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentCallbackPresentationV2 implements IPaymentPresentation<PaymentCallbackResponse, CallbackResponseWrapper<AbstractPaymentCallbackResponse>> {
    @Override
    public WynkResponseEntity<PaymentCallbackResponse> transform (CallbackResponseWrapper<AbstractPaymentCallbackResponse> payload) {
        final DefaultPaymentCallbackResponse response = (DefaultPaymentCallbackResponse) payload.getCallbackResponse();
        return (Objects.nonNull(response.getRedirectUrl())) ?
                WynkResponseUtils.redirectResponse(response.getRedirectUrl()) :
                WynkResponseEntity.<PaymentCallbackResponse>builder().data(DefaultCallbackResponse.builder().transactionStatus(response.getTransactionStatus()).redirectUrl(response.getRedirectUrl()).build()).build();
    }
}