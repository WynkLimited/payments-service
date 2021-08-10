package in.wynk.payment.dto.response;

import in.wynk.payment.core.dao.entity.PaymentMethod;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PaymentOptionsComputationResponse {
    private final List<PaymentMethod> paymentMethods;
}
