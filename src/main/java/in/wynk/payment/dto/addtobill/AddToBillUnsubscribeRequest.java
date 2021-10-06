package in.wynk.payment.dto.addtobill;

import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class AddToBillUnsubscribeRequest {
    private String msisdn;
    private String productCode;
    private String provisionSi;
    private String source;
}
