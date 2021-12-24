package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class WalletValidateLinkRequest extends WalletRequest {

    @NotBlank
    @Analysed
    private String otp;

    @NotBlank
    private String otpToken;

    @NotBlank
    private String walletUserId;

    public abstract String getUid();

    public abstract String getMsisdn();

    public abstract String getService();

}