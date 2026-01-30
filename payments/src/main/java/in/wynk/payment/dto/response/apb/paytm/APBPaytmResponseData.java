package in.wynk.payment.dto.response.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class APBPaytmResponseData {
    private String otpToken;
    private String wallet;
    private double balance;
    private String customerId;
    private String additionalParams;
    private String encryptedToken;

    private String pgId;
    private String paymentGateway;
    private String paymentStatus;
    private String payUrl;
    private String html;


}
