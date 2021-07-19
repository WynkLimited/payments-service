package in.wynk.payment.dto.response.phonepe.auto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhonePeChargingRequest<T extends IPurchaseDetails> extends AbstractChargingRequest<T> {
    @Analysed
    private Long phonePeVersionCode;
}
