package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
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
public class WebPaymentAccountDeletionRequest extends AbstractPaymentAccountDeletionRequest {

    @Override
    public String getMsisdn() {
        return ((SessionDTO) SessionContextHolder.getBody()).get("msisdn");
    }
}
