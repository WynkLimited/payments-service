package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class WalletTopUpResponse {
    @Analysed
    private String info;
}
