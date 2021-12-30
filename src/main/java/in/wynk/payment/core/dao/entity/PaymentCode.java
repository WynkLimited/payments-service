package in.wynk.payment.core.dao.entity;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.data.entity.MongoBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_GATEWAY;

@Getter
@SuperBuilder
@AnalysedEntity
@Document("payment_codes")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentCode extends MongoBaseEntity<String> {

    @Field("bean_name")
    @Analysed(name = PAYMENT_GATEWAY)
    private String code;

    @Field("pre_debit")
    private boolean preDebit;

    @Field("internal_recurring")
    private boolean internalRecurring;

    @Field("trial_refund_supported")
    private boolean trialRefundSupported;

    @Field("external_activation_not_required")
    private boolean externalActivationNotRequired;

    public String name() {
        return this.getId();
    }

}