package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.commons.enums.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Builder
@Getter
@AnalysedEntity
public class PaymentOptionsDTO {

    private List<PaymentGroupsDTO> paymentGroups;
    private double discountedPrice;
    private double price;
    private boolean priceChanged;

    @Builder
    @Getter
    @AnalysedEntity
    public static class PaymentGroupsDTO {
        List<PaymentMethodDTO> paymentMethods;
        private PaymentGroup paymentGroup;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    @AnalysedEntity
    public static class PaymentMethodDTO {
        @Analysed
        private String group;
        @Analysed
        private Map<String, Object> meta;
        @Analysed
        private String displayName;
        @Analysed
        private String paymentCode;

        public PaymentMethodDTO(PaymentMethod method) {
            this.group = method.getGroup().name();
            this.meta = method.getMeta();
            this.displayName = method.getDisplayName();
            this.paymentCode = method.getPaymentCode().name();
        }
    }
}
