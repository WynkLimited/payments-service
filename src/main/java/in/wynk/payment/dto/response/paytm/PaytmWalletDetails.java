package in.wynk.payment.dto.response.paytm;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractWalletDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PaytmWalletDetails extends AbstractWalletDetails {

    @Analysed
    private String displayName;
    @Analysed
    private double expiredAmount;
    @Analysed
    private boolean fundSufficient;
    @Analysed
    private boolean addMoneyAllowed;

}
