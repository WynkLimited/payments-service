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
public class WalletAddMoneyRequest extends WalletRequest {

    @Analysed
    private int planId;

    @Analysed
    private String itemId;

    @Analysed
    private double amountToCredit;

    @Analysed
    private long phonePeVersionCode;

}