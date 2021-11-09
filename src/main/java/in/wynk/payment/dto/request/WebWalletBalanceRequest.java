package in.wynk.payment.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static in.wynk.common.constant.BaseConstants.CLIENT;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public class WebWalletBalanceRequest extends WalletBalanceRequest {

    @Override
    @JsonIgnore
    public String getDeviceId() {
        final SessionDTO session = SessionContextHolder.getBody();
        return session.get(BaseConstants.DEVICE_ID);
    }

    @Override
    @JsonIgnore
    public String getMsisdn() {
        final SessionDTO session = SessionContextHolder.getBody();
        return session.get(BaseConstants.MSISDN);
    }

    @Override
    @JsonIgnore
    public String getService() {
        final SessionDTO session = SessionContextHolder.getBody();
        return session.get(BaseConstants.SERVICE);
    }

    @Override
    @JsonIgnore
    public String getUid() {
        final SessionDTO session = SessionContextHolder.getBody();
        return session.get(BaseConstants.UID);
    }

    @Override
    @JsonIgnore
    public String getClient() {
        return SessionContextHolder.<SessionDTO>getBody().get(CLIENT);
    }

}
