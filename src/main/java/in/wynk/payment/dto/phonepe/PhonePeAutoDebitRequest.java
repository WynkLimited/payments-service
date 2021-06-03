package in.wynk.payment.dto.phonepe;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class PhonePeAutoDebitRequest {

    private String merchantId;
    private String userAuthToken;

}