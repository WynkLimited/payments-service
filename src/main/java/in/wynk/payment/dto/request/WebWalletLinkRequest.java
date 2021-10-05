package in.wynk.payment.dto.request;


import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static in.wynk.common.constant.BaseConstants.CLIENT;
import static in.wynk.common.constant.BaseConstants.DEVICE_ID;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public class WebWalletLinkRequest extends WalletLinkRequest {

    @Override
    @JsonIgnore
    public String getDeviceId() {
        final SessionDTO session = SessionContextHolder.getBody();
        return session.get(DEVICE_ID);
    }

    @Override
    @JsonIgnore
    public String getClient() {
        return SessionContextHolder.<SessionDTO>getBody().get(CLIENT);
    }
}
