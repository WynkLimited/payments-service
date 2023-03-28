package in.wynk.payment.dto.aps.response.option;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.math.BigDecimal;
import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WalletPaymentsOptions extends AbstractPaymentOptions {
private List<SubOption> subOption;

    @Getter
    @SuperBuilder
    public static class SubOption{
        private String type;
        private String subType;
        private String health;
        private boolean recommended;
        private String iconUrl;
        private BigDecimal minAmount;
        private boolean insufficientFlowDisabled;
        private boolean insufficientFlowDisabledLabel;
    }
}
