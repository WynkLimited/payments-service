package in.wynk.payment.dto.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Getter
@ToString
@SuperBuilder
public class PhonePeAutoDebitOtpRequest extends PhonePeAutoDebitRequest {

    @Analysed
    private String otp;
    @Analysed
    private String otpToken;

}