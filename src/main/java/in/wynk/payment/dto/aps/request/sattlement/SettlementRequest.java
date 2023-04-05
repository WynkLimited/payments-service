package in.wynk.payment.dto.aps.request.sattlement;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.List;

@Getter
@Builder
@ToString
public class SettlementRequest {

    private String orderId;
    private String channel;
    private PaymentDetails paymentDetails;
    private List<OrderDetails> serviceOrderDetails;

    @Getter
    @Builder
    @ToString
    public static class PaymentDetails {
        private double orderPaymentAmount;
        private String paymentTransactionId;
    }

    @Getter
    @Builder
    @ToString
    public static class OrderDetails {
        private String serviceOrderId;
        private String serviceId;
        private PaymentDetails paymentDetails;

        @Getter
        @Builder
        @ToString
        public static class PaymentDetails {
            private String paymentAmount;
        }

    }
}
