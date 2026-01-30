package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@Builder
@Getter
@AnalysedEntity
public class PaymentOptionsDTO {

    private final String msisdn;
    private final IProductDetails productDetails;
    private final PlanDetails planDetails;
    private final List<PaymentGroupsDTO> paymentGroups;

    @SuperBuilder
    @Getter
    @AnalysedEntity
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class PlanDetails implements IProductDetails {
        private final String id;
        private final String partnerName;
        private final String partnerLogo;
        private final String title;
        private final String discount;
        private final String validityUnit;
        private final Integer month;
        private final int perMonthValue;
        private final double price;
        private final double discountedPrice;
        private final boolean freeTrialAvailable;
        private final Double dailyAmount;
        private final Integer day;
        private final TrialPlanDetails trialDetails;
        private final String type = BaseConstants.PLAN;
        private final String currency;
        private Map<String, String> sku;
        private final String subType;
    }

    @SuperBuilder
    @Getter
    @AnalysedEntity
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class TrialPlanDetails implements IProductDetails {
        private final String id;
        private String title;
        private final String validityUnit;
        private final String currency;

        private final int validity;

        private final Integer day;
        private final Integer month;
        private final TimeUnit timeUnit;
        private final String type = BaseConstants.PLAN;
    }

    @SuperBuilder
    @Getter
    @AnalysedEntity
    public static class PointDetails implements IProductDetails {
        private final String id;
        private final String title;
        private final double price;
        private final String type = BaseConstants.POINT;
    }

    @Builder
    @Getter
    @AnalysedEntity
    public static class PaymentGroupsDTO {
        private final List<PaymentMethodDTO> paymentMethods;
        private final int hierarchy;
        private final String paymentGroup;
        private final String displayName;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    @AnalysedEntity
    public static class PaymentMethodDTO implements Serializable {
        @Analysed
        private final String paymentId;
        @Analysed
        private final String group;
        @Analysed
        private final int hierarchy;
        @Analysed
        private final Map<String, Object> meta;
        @Analysed
        private final String displayName;
        @Analysed
        private final String paymentCode;
        private final String subtitle;
        private final String iconUrl;
        private final String tag;
        private final boolean autoRenewSupported;
        private final boolean mandateSupported;
        private final List<String> suffixes;

        public PaymentMethodDTO(PaymentMethod method, Supplier<Boolean> autoRenewSupplier) {
            this.paymentId = method.getId();
            this.group = method.getGroup();
            this.meta = method.getMeta();
            this.tag = method.getTag();
            this.hierarchy = method.getHierarchy();
            this.displayName = method.getDisplayName();
            this.paymentCode = method.getPaymentCode().name();
            this.subtitle = method.getSubtitle();
            this.iconUrl = method.getIconUrl();
            this.autoRenewSupported = autoRenewSupplier.get() && method.isAutoRenewSupported();
            this.mandateSupported = method.isMandateSupported();
            this.suffixes = method.getSuffixes();
        }
    }
}
