package in.wynk.payment.presentation;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.WynkResponseUtils;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.gateway.callback.DefaultPaymentCallbackResponse;
import in.wynk.payment.dto.manager.CallbackResponseWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

@Component
@RequiredArgsConstructor
public class PaymentCallbackPresentation implements IPaymentPresentation<AbstractPaymentCallbackResponse, CallbackResponseWrapper<?>> {

    @Override
    public WynkResponseEntity<AbstractPaymentCallbackResponse> transform(CallbackResponseWrapper<?> payload) {

        final DefaultPaymentCallbackResponse response = (DefaultPaymentCallbackResponse) payload.getCallbackResponse();
        return (Objects.nonNull(response.getRedirectUrl())) ?
                WynkResponseUtils.redirectResponse(response.getRedirectUrl()) :
                WynkResponseEntity.<AbstractPaymentCallbackResponse>builder().data(DefaultPaymentCallbackResponse.builder().transactionStatus(response.getTransactionStatus()).build()).build();
    }
}
