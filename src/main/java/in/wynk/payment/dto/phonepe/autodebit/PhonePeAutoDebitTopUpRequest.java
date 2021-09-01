package in.wynk.payment.dto.phonepe.autodebit;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.dto.request.WalletTopUpRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public class PhonePeAutoDebitTopUpRequest<T extends IPurchaseDetails> extends WalletTopUpRequest<T> {
    @NotNull
    @Analysed
    private Long phonePeVersionCode;
}