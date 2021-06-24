package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.enums.ChargingType;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractChargingRequest<T extends AbstractChargingRequest.IChargingDetails> {

    @Analysed
    private PaymentCode paymentCode;
    @Analysed
    private T chargingDetails;

    @AnalysedEntity
    public interface IChargingDetails {
        String getCouponId();
        String getPaymentMode();
        String getMerchantName();
        ChargingType getType();
    }

    @AnalysedEntity
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AbstractChargingRequest.PlanWebChargingDetails.class, name = "WEB_PLAN"),
            @JsonSubTypes.Type(value = AbstractChargingRequest.PointWebChargingDetails.class, name = "WEB_POINT")
    })
    public interface IWebChargingDetails extends IChargingDetails { }

    @AnalysedEntity
    @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.EXISTING_PROPERTY, property = "type")
    @JsonSubTypes({
            @JsonSubTypes.Type(value = AbstractChargingRequest.PlanS2SChargingDetails.class, name = "S2S_PLAN"),
            @JsonSubTypes.Type(value = AbstractChargingRequest.PointS2SChargingDetails.class, name = "S2S_POINT")
    })
    public interface IS2SChargingDetails extends IChargingDetails {
        String getMsisdn();
    }

    @Getter
    @SuperBuilder
    @AnalysedEntity
    @NoArgsConstructor
    @AllArgsConstructor
    public static abstract class AbstractPlanChargingDetails {
        @Analysed
        private int planId;
        @Analysed
        private String couponId;
        @Analysed(name = "paymentMode")
        private String paymentMode;
        @Analysed(name = "bankName")
        private String merchantName;
        @Analysed
        private boolean autoRenew;
        @Analysed
        private boolean trialOpted;
    }

    @Getter
    @SuperBuilder
    @AnalysedEntity
    @NoArgsConstructor
    @AllArgsConstructor
    public static abstract class AbstractPointChargingDetails {
        @Analysed
        private String couponId;
        @Analysed(name = "paymentMode")
        private String paymentMode;
        @Analysed(name = "bankName")
        private String merchantName;
        @Analysed
        private String itemId;
    }

    @Getter
    @SuperBuilder
    @AnalysedEntity
    public static class PlanS2SChargingDetails extends AbstractPlanChargingDetails implements IS2SChargingDetails {
        @Analysed
        private String uid;
        @Analysed
        private String msisdn;

        @Override
        public ChargingType getType() {
            return ChargingType.S2S_PLAN;
        }
    }

    @Getter
    @SuperBuilder
    @AnalysedEntity
    public static class PointS2SChargingDetails extends AbstractPointChargingDetails implements IS2SChargingDetails {

        @Analysed
        private String msisdn;
        @Analysed
        private double amount;

        @Override
        public ChargingType getType() {
            return ChargingType.S2S_POINT;
        }
    }

    @Getter
    @SuperBuilder
    @AnalysedEntity
    public static class PlanWebChargingDetails extends AbstractPlanChargingDetails implements IWebChargingDetails {

        @Override
        public ChargingType getType() {
            return ChargingType.WEB_PLAN;
        }
    }

    @Getter
    @SuperBuilder
    @AnalysedEntity
    public static class PointWebChargingDetails extends AbstractPointChargingDetails implements IWebChargingDetails {

        @Override
        public ChargingType getType() {
            return ChargingType.WEB_POINT;
        }
    }

}
