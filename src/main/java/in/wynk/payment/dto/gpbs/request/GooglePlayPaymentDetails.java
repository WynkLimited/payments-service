package in.wynk.payment.dto.gpbs.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.PaymentDetails;
import lombok.*;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@Builder
@ToString
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
public class GooglePlayPaymentDetails {
    @Analysed
    @NotNull
    private String purchaseToken;

    @Analysed
    @NotNull
    private String orderId;

    @Analysed
    private Integer notificationType = 4;

    @Analysed
    private boolean valid;

}
