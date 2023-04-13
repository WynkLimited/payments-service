package in.wynk.payment.dto.request;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.dto.ChecksumHeaderCallbackRequest;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.gateway.IPaymentCallback;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;

import java.util.Map;
import java.util.Objects;


/**
 * @author Nishesh Pandey
 */
@Getter
public class CallbackRequestWrapperV2<T extends CallbackRequest> extends CallbackRequest {

    private final T body;
    private final String transactionId;
    private final PaymentGateway paymentGateway;

    protected CallbackRequestWrapperV2(CallbackRequestWrapperV2Builder<T, ?, ?> b) {
        super(b);
        this.body = b.body;
        this.transactionId = b.transactionId;
        this.paymentGateway = b.paymentGateway;
    }

    public static <T extends CallbackRequest> CallbackRequestWrapperV2Builder<T, ?, ?> builder() {
        return new CallbackRequestWrapperV2BuilderImpl<>();
    }

    @Override
    public String getTransactionId() {
        if (StringUtils.isNotEmpty(transactionId)) {
            return transactionId;
        }
        return body.getTransactionId();
    }

    public static abstract class CallbackRequestWrapperV2Builder<T extends CallbackRequest, C extends CallbackRequestWrapperV2<T>, B extends CallbackRequestWrapperV2Builder<T, C, B>> extends CallbackRequestBuilder<C, B> {
        private T body;
        private String transactionId;
        private PaymentGateway paymentGateway;

        /**
         * Always supply payment code first before payload
         **/
        public B payload(Map<String, Object> payload) {
            if (Objects.isNull(paymentGateway))
                throw new WynkRuntimeException("You must supply payment code first, before supplying payload");
            this.body = BeanLocatorFactory.getBean(paymentGateway.getCode(), new ParameterizedTypeReference<IPaymentCallback<AbstractPaymentCallbackResponse, T>>() {
            }).parse(payload);
            return self();
        }

        public B headers(HttpHeaders headers) {
            if (Objects.nonNull(this.body) && ChecksumHeaderCallbackRequest.class.isAssignableFrom(this.body.getClass()))
                ((ChecksumHeaderCallbackRequest<?>) this.body).withHeader(headers);
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
            return "CallbackRequestWrapperV2.CallbackRequestWrapperBuilder(super=" + super.toString() + ", body=" + this.body + ", transactionId=" + this.transactionId + ", paymentCode=" + this.paymentGateway + ")";
        }
    }

    private static final class CallbackRequestWrapperV2BuilderImpl<T extends CallbackRequest> extends CallbackRequestWrapperV2Builder<T, CallbackRequestWrapperV2<T>, CallbackRequestWrapperV2BuilderImpl<T>> {
        private CallbackRequestWrapperV2BuilderImpl() {
        }

        protected CallbackRequestWrapperV2BuilderImpl<T> self() {
            return this;
        }

        public CallbackRequestWrapperV2<T> build() {
            return new CallbackRequestWrapperV2<>(this);
        }
    }
}
