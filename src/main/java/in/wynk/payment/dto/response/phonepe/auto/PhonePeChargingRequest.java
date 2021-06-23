package in.wynk.payment.dto.response.phonepe.auto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PhonePeChargingRequest<T extends AbstractChargingRequest.IChargingDetails> extends AbstractChargingRequest<T> {
    @Analysed
    private Long phonePeVersionCode;
}
