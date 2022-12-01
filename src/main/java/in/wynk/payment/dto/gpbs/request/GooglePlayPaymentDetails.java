package in.wynk.payment.dto.gpbs.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.*;

import javax.validation.constraints.NotEmpty;

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
    @NotEmpty
    private String purchaseToken;

    @Analysed
    private String orderId;

    @Analysed
    private Integer notificationType = 4;

    @Analysed
    private boolean valid;

}
