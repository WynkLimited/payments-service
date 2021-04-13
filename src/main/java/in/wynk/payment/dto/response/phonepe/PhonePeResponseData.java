package in.wynk.payment.dto.response.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.dto.phonepe.PhonePeWallet;
import lombok.*;
import lombok.experimental.SuperBuilder;
import springfox.documentation.service.ApiListing;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class PhonePeResponseData{
    @Analysed
    private String message;
    @Analysed
    private String merchantId;
    @Analysed
    private String otpToken;
    @Analysed
    private String userAuthToken;
    @Analysed
    Boolean linkedUser;
    @Analysed
    String userIdHash;
    @Analysed
    String maskedMobileNumber;
    @Analysed
    String redirectUrl;
    @Analysed
    String responseType;
    @Analysed
    String  transactionId;
    @Analysed
    long   amount;
    @Analysed
    String  paymentState;
    @Analysed
    String  providerReferenceId;
    @Analysed
    private PhonePeWallet wallet;
}
