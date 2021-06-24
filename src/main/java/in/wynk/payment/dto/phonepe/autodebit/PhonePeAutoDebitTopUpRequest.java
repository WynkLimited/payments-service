package in.wynk.payment.dto.phonepe.autodebit;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.dto.request.WalletTopUpRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PhonePeAutoDebitTopUpRequest<T extends AbstractChargingRequest.IChargingDetails> extends WalletTopUpRequest<T> {
    @Analysed
    private long phonePeVersionCode;
}
