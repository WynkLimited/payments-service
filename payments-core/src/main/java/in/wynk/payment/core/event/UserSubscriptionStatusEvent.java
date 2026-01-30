package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AnalysedEntity
public class UserSubscriptionStatusEvent {
    @Analysed
    private String transactionId;
    @Analysed
    private String si;
    @Analysed
    private String productCode;
    @Analysed
    private double productPrice;
    @Analysed
    private String chargingCycle;
    @Analysed
    private String subscriptionStartDate;
    @Analysed
    private String renewalDate;
    @Analysed
    private String status;
}
