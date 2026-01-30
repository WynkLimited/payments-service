package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static in.wynk.common.constant.BaseConstants.*;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public class WebWalletDeLinkRequest extends WalletDeLinkRequest {

    @Override
    @JsonIgnore
    public String getDeviceId() {
        final SessionDTO session = SessionContextHolder.getBody();
        return session.get(DEVICE_ID);
    }

    @Override
    @JsonIgnore
    public String getMsisdn() {
        final SessionDTO session = SessionContextHolder.getBody();
        return session.get(MSISDN);
    }

    @Override
    @JsonIgnore
    public String getService() {
        final SessionDTO session = SessionContextHolder.getBody();
        return session.get(SERVICE);
    }

    @Override
    @JsonIgnore
    public String getUid() {
        final SessionDTO session = SessionContextHolder.getBody();
        return session.get(UID);
    }

    @Override
    @JsonIgnore
    public String getClient() {
        return SessionContextHolder.<SessionDTO>getBody().get(CLIENT);
    }
}
