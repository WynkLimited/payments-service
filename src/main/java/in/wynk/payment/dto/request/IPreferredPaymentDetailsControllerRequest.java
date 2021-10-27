package in.wynk.payment.dto.request;

import java.util.List;
import java.util.Map;

public interface IPreferredPaymentDetailsControllerRequest {

    String getUid();
    String getClient();
    String getDeviceId();
    Map<String, List<String>> getPaymentGroups();

}