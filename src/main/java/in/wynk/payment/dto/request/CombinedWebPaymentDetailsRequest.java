package in.wynk.payment.dto.request;

import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.service.impl.AbstractPreferredPaymentDetailsRequest;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.CLIENT;

@Getter
@SuperBuilder
@AllArgsConstructor
@NoArgsConstructor
public class CombinedWebPaymentDetailsRequest<T extends IProductDetails> extends AbstractPreferredPaymentDetailsRequest<T> {
    private Map<String, List<String>> paymentGroups;

    public String getClient() {
        return SessionContextHolder.<SessionDTO>getBody().get(CLIENT);
    }
}
