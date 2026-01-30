package in.wynk.payment.dto.response.paytm;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PaytmCallbackResponse extends AbstractCallbackResponse {
    @Analysed
    private String info;

    @Analysed
    private String redirectUrl;

    @Analysed
    private boolean deficit;
}
