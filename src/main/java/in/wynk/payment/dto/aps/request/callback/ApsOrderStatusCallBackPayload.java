package in.wynk.payment.dto.aps.request.callback;

import com.fasterxml.jackson.annotation.JsonIgnore;
import in.wynk.payment.dto.aps.response.order.ApsS2SOrderInfo;
import in.wynk.payment.dto.aps.response.order.FulfilmentInfo;
import in.wynk.payment.dto.aps.response.order.OrderPaymentDetails;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class ApsOrderStatusCallBackPayload extends ApsCallBackRequestPayload {
    private ApsS2SOrderInfo orderInfo;
    private OrderPaymentDetails[] paymentDetails;
    private FulfilmentInfo[] fulfilmentInfo;
    private String hash;
    @JsonIgnore
    @Setter
    private String txnId;

    @JsonIgnore
    public String getTransactionId() {
        return this.getTxnId();
    }

}
