package in.wynk.payment.dto.request;

import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.*;

@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class CombinedWebPaymentDetailsRequest extends AbstractPreferredPaymentDetailsControllerRequest {

    private Map<String, List<String>> paymentGroups;

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