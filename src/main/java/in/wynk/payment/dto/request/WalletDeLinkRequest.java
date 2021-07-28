package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class WalletDeLinkRequest extends WalletRequest {

    public abstract String getUid();
    public abstract String getMsisdn();

}
