package in.wynk.payment.dto.request;

import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.service.impl.AbstractPreferredPaymentDetailsRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CombinedPaymentDetailsRequest<T extends IProductDetails> extends AbstractPreferredPaymentDetailsRequest<T> {
    private Map<String, List<String>> paymentGroups;
}
