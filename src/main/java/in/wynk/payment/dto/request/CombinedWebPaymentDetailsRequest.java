package in.wynk.payment.dto.request;

import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    public String getDeviceId() {
        return SessionContextHolder.<SessionDTO>getBody().get(DEVICE_ID);
    }

}