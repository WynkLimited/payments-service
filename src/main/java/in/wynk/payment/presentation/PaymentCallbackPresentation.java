package in.wynk.payment.presentation;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.gateway.IRedirectSpec;
import in.wynk.payment.dto.gateway.IUpiIntentSpec;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.gateway.callback.DefaultPaymentCallbackResponse;
import in.wynk.payment.dto.manager.CallbackResponseWrapper;
import in.wynk.payment.dto.manager.ChargingGatewayResponseWrapper;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.DefaultCallbackResponse;
import in.wynk.payment.dto.response.presentation.card.NonSeamlessCardChargingResponse;
import in.wynk.payment.dto.response.presentation.card.SeamlessCardChargingResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class PaymentCallbackPresentation {

    private final Map<String, IPaymentPresentation<? extends AbstractPaymentCallbackResponse, CallbackResponseWrapper<?>>> delegate = new HashMap<>();

    @PostConstruct
    public void init() {
        delegate.put("DEFAULT", new DefaultCallbackPresentation());
    }

    public class DefaultCallbackPresentation implements IPaymentPresentation<DefaultPaymentCallbackResponse, CallbackResponseWrapper<?>> {

        @Override
        public WynkResponseEntity<DefaultPaymentCallbackResponse> transform(CallbackResponseWrapper<?> payload) {
            final IRedirectSpec redirectSpec = (IRedirectSpec) payload.getCallbackResponse();
            return WynkResponseEntity.<DefaultPaymentCallbackResponse>builder().data(DefaultPaymentCallbackResponse.builder().redirectUrl(redirectSpec.getRedirectUrl()).build()).build();
        }
    }
}
