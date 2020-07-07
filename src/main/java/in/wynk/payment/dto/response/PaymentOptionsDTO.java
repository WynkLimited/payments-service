package in.wynk.payment.dto.response;

import in.wynk.commons.enums.PaymentGroup;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Builder
@Data
public class PaymentOptionsDTO {

    private List<PaymentGroupsDTO> paymentGroups;
    private Double discountedPrice;
    private Double price;
    private Boolean priceChanged;

    @Builder
    @Data
    public static class PaymentGroupsDTO {
        List<PaymentMethodDTO> paymentMethods;
        private PaymentGroup paymentGroup;
    }

    @Data
    @AllArgsConstructor
    @Builder
    public static class PaymentMethodDTO {
        private String group;
        private Map<String, Object> meta;
        private String displayName;
        private String paymentCode;

        public PaymentMethodDTO(PaymentMethod method) {
            this.group = method.getGroup().name();
            this.meta = method.getMeta();
            this.displayName = method.getDisplayName();
            this.paymentCode = method.getPaymentCode().name();
        }
    }
}
