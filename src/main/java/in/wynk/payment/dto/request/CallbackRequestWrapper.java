package in.wynk.payment.dto.request;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;

import java.util.Map;
import java.util.Objects;

@Getter
public class CallbackRequestWrapper<T extends CallbackRequest> extends CallbackRequest {

    private final T body;
    private final String transactionId;
    private final PaymentGateway paymentGateway;

    protected CallbackRequestWrapper(CallbackRequestWrapperBuilder<T, ?, ?> b) {
        super(b);
        this.body = b.body;
        this.transactionId = b.transactionId;
        this.paymentGateway = b.paymentGateway;
    }

    public static <T extends CallbackRequest> CallbackRequestWrapperBuilder<T, ?, ?> builder() {
        return new CallbackRequestWrapperBuilderImpl<>();
    }

    @Override
    public String getTransactionId() {
        if (StringUtils.isNotEmpty(transactionId)) {
            return transactionId;
        }
        return body.getTransactionId();
    }

    public static abstract class CallbackRequestWrapperBuilder<T extends CallbackRequest, C extends CallbackRequestWrapper<T>, B extends CallbackRequestWrapperBuilder<T, C, B>> extends CallbackRequestBuilder<C, B> {
        private T body;
        private String transactionId;
        private PaymentGateway paymentGateway;

        /**
         * Always supply payment code first before payload
         **/
        public B payload(Map<String, Object> payload) {
            if (Objects.isNull(paymentGateway))
                throw new WynkRuntimeException("You must supply payment code first, before supplying payload");
            this.body = BeanLocatorFactory.getBean(paymentGateway.getCode(), new ParameterizedTypeReference<IMerchantPaymentCallbackService<AbstractCallbackResponse, T>>() {
            }).parseCallback(payload);
            return self();
        }

        /**
         * Optional to supply
         **/
        public B transactionId(String transactionId) {
            this.transactionId = transactionId;
            return self();
        }

        public B paymentGateway(PaymentGateway paymentGateway) {
            this.paymentGateway = paymentGateway;
            return self();
        }

        protected abstract B self();

        public abstract C build();

        public String toString() {
            return "CallbackRequestWrapper.CallbackRequestWrapperBuilder(super=" + super.toString() + ", body=" + this.body + ", transactionId=" + this.transactionId + ", paymentCode=" + this.paymentGateway + ")";
        }
    }

    private static final class CallbackRequestWrapperBuilderImpl<T extends CallbackRequest> extends CallbackRequestWrapperBuilder<T, CallbackRequestWrapper<T>, CallbackRequestWrapperBuilderImpl<T>> {
        private CallbackRequestWrapperBuilderImpl() {
        }

        protected CallbackRequestWrapperBuilderImpl<T> self() {
            return this;
        }

        public CallbackRequestWrapper<T> build() {
            return new CallbackRequestWrapper<>(this);
        }
    }

}