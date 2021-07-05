package in.wynk.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CombinedPaymentDetailsRequest {
    private String couponId;
    private int planId;
    private Map<String, List<String>> paymentGroups;

}
