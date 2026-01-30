package in.wynk.payment.dto.aps.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@SuperBuilder
public class CollectUpiPaymentInfo extends AbstractUpiPaymentInfo {
    private String vpa;
    private final String upiFlow = "COLLECT";
}
