package in.wynk.payment.dto.request;

import in.wynk.payment.core.event.ClientCallbackEvent;
import lombok.*;

@Getter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ClientCallbackRequest {

    private String uid;
    private String msisdn;
    private String itemId;
    private Integer planId;
    private String transactionId;
    private String transactionStatus;

    public static ClientCallbackRequest from(ClientCallbackEvent event) {
        return ClientCallbackRequest.builder()
                .uid(event.getUid())
                .msisdn(event.getMsisdn())
                .itemId(event.getItemId())
                .planId(event.getPlanId())
                .transactionId(event.getTransactionId())
                .transactionStatus(event.getTransactionStatus())
                .build();
    }

}
