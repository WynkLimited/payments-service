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
    private String code;
    @Analysed
    private String merchantId;
    @Analysed
    private String otpToken;
    @Analysed
    private String userAuthToken;
    @Analysed
    private Boolean linkedUser;
    @Analysed
    private String userIdHash;
    @Analysed
    private String maskedMobileNumber;
    @Analysed
    private String redirectUrl;
    @Analysed
    private String responseType;
    @Analysed
    private String  transactionId;
    @Analysed
    private Long   amount;
    @Analysed
    private String  paymentState;
    @Analysed
    private String  providerReferenceId;
    @Analysed
    private PhonePeWallet wallet;
}
