package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractPreDebitNotificationMessage {
    @Analysed
    private String transactionId;
}
