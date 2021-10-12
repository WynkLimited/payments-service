package in.wynk.payment.dto.response.addtobill;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class AddToBillOrder {
    private String orderId;
    private String serviceId;
    private String si;
    private String loggedInSi;
    private String orderStatus;
    private Date orderCreationDate;
    private String requestId;
    private String serviceStatus;
    private Date startDate;
    private Date endDate;
    private Map orderMeta;

}
