package in.wynk.payment.dto.addtobill;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;
@Getter
@Builder
public class AddToBillCheckOutRequest {
    private String channel;
    private String loggedInSi;
    private Map orderMeta;
    private OrderPaymentDetails orderPaymentDetails;
    private List<ServiceOrderItem> serviceOrderItems;

}
