package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class WalletBalanceRequest extends WalletRequest {
    @Analysed
    private int planId;
    @Analysed
    private String couponId;

    public abstract String getMsisdn();
    public abstract String getUid();
}
