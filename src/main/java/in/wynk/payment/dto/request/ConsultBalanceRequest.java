package in.wynk.payment.dto.request;

import lombok.*;

@Builder
@RequiredArgsConstructor
@AllArgsConstructor
@Getter
@ToString
public class ConsultBalanceRequest {

    private ConsultBalanceRequestHead head;

    private ConsultBalanceRequestBody body;

    @Builder
    @Getter
    @RequiredArgsConstructor
    @AllArgsConstructor
    public static class ConsultBalanceRequestHead {
        private String clientId;
        private String requestTimestamp;
        private String signature;
        private String version;
        private String channelId;
    }

    @Builder
    @Getter
    @RequiredArgsConstructor
    @AllArgsConstructor
    public static class ConsultBalanceRequestBody {
        private String userToken;
        private Double totalAmount;
        private String mid;
    }

}
