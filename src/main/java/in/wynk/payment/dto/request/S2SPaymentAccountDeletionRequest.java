package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.client.context.ClientContext;
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
public class S2SPaymentAccountDeletionRequest extends AbstractPaymentAccountDeletionRequest {
    private String msisdn;

    //TODO: Load client using loadUtils when endpoint for S2S is written/required
    @Override
    public String getClient () {
        return ClientContext.getClient().toString();
    }
}
