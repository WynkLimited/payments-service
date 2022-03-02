package in.wynk.payment.dto.aps.saved.details;

import in.wynk.payment.dto.aps.common.*;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;

@Getter
@ToString
public class ApsSavedPaymentInfoResponse {

    private List<SavedVPADetailsWrapper> cardDetails;
    private List<SavedCardDetailsWrapper> vpaDetails;
    private List<SavedWalletDetailsWrapper> walletDetails;
    private Map<HealthStatus, ApsHealthCheckConfig> healthCheckConfig;

    @Getter
    @ToString
    private static class SavedCardDetailsWrapper {
        private SavedCardDetail cardDetail;
        private String healthState;
    }

    @Getter
    @ToString
    private static class SavedVPADetailsWrapper {
        private VpaDetail vpaDetail;
        private String healthState;
    }

    @Getter
    @ToString
    private static class SavedWalletDetailsWrapper {
        private SavedWalletDetail walletDetail;
        private String healthState;
    }


}
