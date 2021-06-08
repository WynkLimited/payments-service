package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class AbstractWalletDetails extends AbstractPaymentDetails {
    @Analysed
    private double balance;
    @Analysed
    private double deficitBalance;
    @Analysed
    private boolean linked;
    @Analysed
    private String walletCode;
    @Analysed
    private String linkedMobileNo;
}
