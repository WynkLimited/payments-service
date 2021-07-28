package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class WebWalletBalanceRequest extends WalletBalanceRequest {

    @Override
    @JsonIgnore
    public String getDeviceId() {
        final SessionDTO session = SessionContextHolder.getBody();
        return session.get(BaseConstants.DEVICE_ID);
    }

}
