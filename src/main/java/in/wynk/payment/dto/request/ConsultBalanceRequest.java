package in.wynk.payment.dto.request;

import lombok.*;

import java.math.BigDecimal;
import java.util.Map;

@NoArgsConstructor
@AllArgsConstructor
@Data
@Builder
public class ConsultBalanceRequest {

    private ConsultBalanceRequestHead head;

    private ConsultBalanceRequestBody body;

    @Builder
    @Data
    public static class ConsultBalanceRequestHead {
        private String clientId;
        private String requestTimestamp;
        private String signature;
        private String version;
        private String channelId;
    }

    @Builder
    @Data
    public static class ConsultBalanceRequestBody {
        private String userToken;
        private BigDecimal totalAmount;
        private String mid;
        private Map<String, BigDecimal> amountDetails;
    }

}
