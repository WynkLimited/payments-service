package in.wynk.payment.dto.addtobill;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class OptedPaymentMode {
    private String modeId;
    private String modeType;
}
