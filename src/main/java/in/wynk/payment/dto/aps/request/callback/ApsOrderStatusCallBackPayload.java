package in.wynk.payment.dto.aps.request.callback;

import in.wynk.payment.dto.aps.response.order.FulfilmentInfo;
import in.wynk.payment.dto.aps.response.order.OrderInfo;
import in.wynk.payment.dto.aps.response.order.OrderPaymentDetails;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class ApsOrderStatusCallBackPayload extends ApsCallBackRequestPayload {
    private OrderInfo orderInfo;
    private OrderPaymentDetails[] paymentDetails;
    private FulfilmentInfo[] fulfilmentInfo;
    private String hash;
}
