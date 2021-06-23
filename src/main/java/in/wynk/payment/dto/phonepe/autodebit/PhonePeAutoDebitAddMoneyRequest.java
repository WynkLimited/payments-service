package in.wynk.payment.dto.phonepe.autodebit;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.request.WalletAddMoneyRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PhonePeAutoDebitAddMoneyRequest extends WalletAddMoneyRequest {
    @Analysed
    private long phonePeVersionCode;
}
