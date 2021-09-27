package in.wynk.payment.dto.addtobill;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddToBillChargingRequest <T extends IPurchaseDetails> extends AbstractChargingRequest<T> {
    private String channel;
    private String loggedInSi;
    private Map orderMeta;
    private OrderPaymentDetails orderPaymentDetails;
    private ServiceOrderItems serviceOrderItems;

    @Getter
    @Builder
    public static class OrderPaymentDetails {
       private boolean addToBill;
       private double orderPaymentAmount;
       private String paymentTransactionId;
       private OptedPaymentMode optedPaymentMode;
    }
    @Getter
    @Builder
    public static class ServiceOrderItems {
        private PaymentDetails paymentDetails;
        private String provisionSi;
        private String serviceId;
        private Map serviceOrderMeta;
    }
    @Getter
    @Builder
    public static class OptedPaymentMode {
        private String modeId;
        private String modeType;
    }

    @Getter
    @Builder
    public static class PaymentDetails {
        private double paymentAmount;
    }

}
