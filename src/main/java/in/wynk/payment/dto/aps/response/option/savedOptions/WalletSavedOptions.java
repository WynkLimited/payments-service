package in.wynk.payment.dto.aps.response.option.savedOptions;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WalletSavedOptions extends AbstractSavedPayOptions {
    private String walletType;
    private String walletId;
    private BigDecimal walletBalance;
    private boolean recommended;
    private boolean isLinked;
    private boolean valid;
}
