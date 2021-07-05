package in.wynk.payment.dto.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class APBPaytmPaymentInfo {
    private String lob;
    private String walletLoginId;
    private String wallet;
    private String encryptedToken;
    private String currency;
    private String paymentMode;
    private double paymentAmount;
}
