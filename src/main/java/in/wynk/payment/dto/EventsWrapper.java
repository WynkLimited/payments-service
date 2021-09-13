package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.dto.request.CallbackRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Builder
@Setter
@Getter
public class EventsWrapper {
    private String os;
    private String deviceId;
    private IAppDetails appDetails;
    private IUserDetails userDetails;
    private IPaymentDetails paymentDetails;
    private IProductDetails productDetails;
    private Transaction transaction;
    private CallbackRequest callbackRequest;
    private PaymentReconciliationMessage paymentReconciliationMessage;
    private String paymentCode;
    private String paymentEvent;
    private boolean optForAutoRenew;
    private String triggerDate;
}
