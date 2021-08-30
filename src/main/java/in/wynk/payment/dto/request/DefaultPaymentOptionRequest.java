package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.IPaymentOptionsRequest;
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
public class DefaultPaymentOptionRequest<T extends IPaymentOptionsRequest> extends AbstractPaymentOptionsRequest<T>  {
}
