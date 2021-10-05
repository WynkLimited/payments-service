package in.wynk.payment.dto.addtobill;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ServiceOrderItem {
    private PaymentDetails paymentDetails;
    private String provisionSi;
    private String serviceId;
    private Map serviceOrderMeta;
}
