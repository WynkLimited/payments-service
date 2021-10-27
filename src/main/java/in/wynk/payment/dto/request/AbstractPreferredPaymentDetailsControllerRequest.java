package in.wynk.payment.dto.request;

import in.wynk.payment.dto.AbstractProductDetails;
import in.wynk.payment.service.impl.AbstractPreferredPaymentDetailsRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractPreferredPaymentDetailsControllerRequest<T extends AbstractProductDetails> extends AbstractPreferredPaymentDetailsRequest<T> implements IPreferredPaymentDetailsControllerRequest {
    private Map<String, List<String>> paymentGroups;
}