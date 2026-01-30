package in.wynk.payment.service;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import static in.wynk.payment.core.constant.PaymentConstants.IAP_PAYMENT_METHODS;

import java.util.Objects;

/**
 * @author Nishesh Pandey
 */
@Service
@Slf4j
public class PaymentGatewayCommon {

    private final IPurchaseDetailsManger purchaseDetailsManger;

    public PaymentGatewayCommon (IPurchaseDetailsManger purchaseDetailsManger) {
        this.purchaseDetailsManger = purchaseDetailsManger;
    }

    public String getPaymentId (Transaction transaction) {
        if (!IAP_PAYMENT_METHODS.contains(transaction.getPaymentChannel().name())) {
            IPurchaseDetails purchaseDetails = purchaseDetailsManger.get(transaction);
            if (Objects.nonNull(purchaseDetails)) {
                return purchaseDetails.getPaymentDetails().getPaymentId();
            }
        }
        return null;
    }
}
