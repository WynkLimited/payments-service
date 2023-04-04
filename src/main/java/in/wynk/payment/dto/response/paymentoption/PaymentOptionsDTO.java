package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.dao.entity.IProductDetails;
import in.wynk.payment.dto.response.AbstractPaymentMethodDTO;
import in.wynk.payment.dto.response.PaymentGroupsDTO;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author Nishesh Pandey
 */
@SuperBuilder
@Getter
@AnalysedEntity
public class PaymentOptionsDTO {
    private final String msisdn;
    private final IProductDetails productDetails;
    @JsonProperty("pay_group_details")
    private final List<PaymentGroupsDTO> paymentGroups;
    @JsonProperty("payment_method_details")
    private final Map<String, List<AbstractPaymentMethodDTO>> paymentMethods;
    @JsonProperty("saved_payment_details")
    private final List<SavedPaymentDTO> savedPaymentDTO;

//    @Getter
//    @Setter
//    @AnalysedEntity
//    public static class PaymentMethodDTO {
//        @JsonProperty("UPI")
//        private List<UPI> upi;
//
//        @JsonProperty("CARD")
//        private List<Card> card;
//
//        @JsonProperty("WALLET")
//        private List<Wallet> wallet;
//
//        @JsonProperty("NET_BANKING")
//        private List<NetBanking> netBanking;
//
//        @JsonProperty("ADD_TO_BILL")
//        private List<AddToBill> addToBills;
//
//        @JsonProperty("BILLING")
//        private List<GooglePlayBilling> billing;
//
//    }

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

}
