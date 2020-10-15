package in.wynk.payment.dto.request;

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

}
