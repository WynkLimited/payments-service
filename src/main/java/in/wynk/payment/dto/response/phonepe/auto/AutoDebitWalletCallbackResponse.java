package in.wynk.payment.dto.response.phonepe.auto;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class AutoDebitWalletCallbackResponse extends AbstractCallbackResponse {
    @Analysed
    private String info;

    @Analysed
    private String redirectUrl;

    @Analysed
    private boolean deficit;
}
