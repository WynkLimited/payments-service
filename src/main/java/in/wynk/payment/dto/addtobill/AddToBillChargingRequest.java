package in.wynk.payment.dto.addtobill;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AddToBillChargingRequest <T extends IPurchaseDetails> extends AbstractChargingRequest<T> {
    private String linkedSi;
    private String serviceId;
}
