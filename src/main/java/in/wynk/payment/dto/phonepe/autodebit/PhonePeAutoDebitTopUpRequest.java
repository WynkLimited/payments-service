package in.wynk.payment.dto.phonepe.autodebit;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.IPurchaseDetails;
import in.wynk.payment.dto.request.WalletTopUpRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public class PhonePeAutoDebitTopUpRequest<T extends IPurchaseDetails> extends WalletTopUpRequest<T> {
    @Analysed
    private long phonePeVersionCode;
}
