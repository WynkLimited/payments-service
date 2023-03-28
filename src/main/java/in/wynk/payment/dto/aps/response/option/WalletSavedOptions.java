package in.wynk.payment.dto.aps.response.option;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WalletSavedOptions extends AbstractSavedPayOptions {
    private String walletType;
    private String walletId;
    private Double walletBalance;
    private boolean recommended;
    private boolean isLinked;
}
