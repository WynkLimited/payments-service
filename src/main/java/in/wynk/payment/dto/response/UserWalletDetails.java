package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.text.DecimalFormat;

@Getter
@SuperBuilder
@AnalysedEntity
public class UserWalletDetails extends AbstractPaymentDetails {

    @Analysed
    private boolean linked;

    @Analysed
    private boolean active;

    @Analysed
    private double balance;

    @Analysed
    private double deficitBalance;

    @Analysed
    private double expiredBalance;

    @Analysed
    private String linkedMobileNo;

    @Analysed
    private boolean addMoneyAllowed;

    public double getDeficitBalance() {
        final DecimalFormat df = new DecimalFormat("#.##");
        return Double.valueOf(df.format(deficitBalance));
    }

}