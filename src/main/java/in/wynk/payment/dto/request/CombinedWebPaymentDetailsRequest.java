package in.wynk.payment.dto.request;

import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import static in.wynk.common.constant.BaseConstants.CLIENT;
import static in.wynk.common.constant.BaseConstants.SI;
import static in.wynk.common.constant.BaseConstants.*;

@Getter
@NoArgsConstructor
public class CombinedWebPaymentDetailsRequest extends AbstractPreferredPaymentDetailsControllerRequest {

    public String getUid() {
        return SessionContextHolder.<SessionDTO>getBody().get(UID);
    }

    public String getClient() {
        return SessionContextHolder.<SessionDTO>getBody().get(CLIENT);
    }

    public String getSi() {
        return SessionContextHolder.<SessionDTO>getBody().get(SI);
    }

    public String getDeviceId() {
        return SessionContextHolder.<SessionDTO>getBody().get(DEVICE_ID);
    }

}

