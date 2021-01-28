package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.enums.PaymentGroup;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Builder
@Getter
@AnalysedEntity
public class PaymentOptionsDTO {

    private final PlanDetails planDetails;
    private final List<PaymentGroupsDTO> paymentGroups;

    @Builder
    @Getter
    @AnalysedEntity
    public static class PlanDetails {
        private final String partnerName;
        private final String partnerLogo;
        private final String discount;
        private final int perMonthValue;
        private final double price;
        private final double discountedPrice;
    }

    @Builder
    @Getter
    @AnalysedEntity
    public static class PaymentGroupsDTO {
        List<PaymentMethodDTO> paymentMethods;
        private final PaymentGroup paymentGroup;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    @AnalysedEntity
    public static class PaymentMethodDTO {
        @Analysed
        private final String group;
        @Analysed
        private final Map<String, Object> meta;
        @Analysed
        private final String displayName;
        @Analysed
        private final String paymentCode;

        public PaymentMethodDTO(PaymentMethod method) {
            this.group = method.getGroup().name();
            this.meta = method.getMeta();
            this.displayName = method.getDisplayName();
            this.paymentCode = method.getPaymentCode().name();
        }
    }
}
