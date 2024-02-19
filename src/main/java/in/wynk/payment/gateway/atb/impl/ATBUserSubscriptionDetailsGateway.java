package in.wynk.payment.gateway.atb.impl;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.UserSubscriptionStatusEvent;
import in.wynk.payment.gateway.atb.ATBUserSubscriptionService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.vas.client.dto.atb.UserSubscriptionStatusResponse;
import in.wynk.vas.client.service.CatalogueVasClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.CANCELLED_STATE;
import static in.wynk.common.constant.BaseConstants.SUBSCRIBED_STATE;

/**
 * @author Nishesh Pandey
 */
@Service
@RequiredArgsConstructor
public class ATBUserSubscriptionDetailsGateway implements ATBUserSubscriptionService {
    private final CatalogueVasClientService catalogueVasClientService;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManagerService;

    public UserSubscriptionStatusResponse getUserSubscriptionDetails (String si, String txnId) {
        ResponseEntity<UserSubscriptionStatusResponse[]> userSubscriptionStatusResponseResponseEntity = null;
        try {
            userSubscriptionStatusResponseResponseEntity = catalogueVasClientService.getUserSubscriptionStatusResponse(si);
            if (Objects.requireNonNull(userSubscriptionStatusResponseResponseEntity.getBody()).length > 0) {
                return userSubscriptionStatusResponseResponseEntity.getBody()[0];
            }
            return UserSubscriptionStatusResponse.builder().si(si).subscriptionStatus(CANCELLED_STATE).build();
        } catch (Exception e) {
            throw new RuntimeException("Exception occurred while finding user subscription status from thanks for the si: " + si, e);
        } finally {
            Transaction transaction = transactionManagerService.get(txnId);
            UserSubscriptionStatusEvent event;
            if ((userSubscriptionStatusResponseResponseEntity != null) && (Objects.requireNonNull(userSubscriptionStatusResponseResponseEntity.getBody()).length > 0)) {
                UserSubscriptionStatusResponse userSubscriptionStatusResponse = userSubscriptionStatusResponseResponseEntity.getBody()[0];
                event = UserSubscriptionStatusEvent.builder().transactionId(txnId).si(si).productCode(userSubscriptionStatusResponse.getProductCode())
                                .productPrice(userSubscriptionStatusResponse.getProductPrice())
                                .chargingCycle(userSubscriptionStatusResponse.getChargingCycle()).subscriptionStartDate(userSubscriptionStatusResponse.getPeriodStartDate())
                                .renewalDate(userSubscriptionStatusResponse.getRenewalDate())
                                .status(SUBSCRIBED_STATE.equals(userSubscriptionStatusResponse.getSubscriptionStatus()) ? userSubscriptionStatusResponse.getSubscriptionStatus() : "CANCELLED").build();
            } else {
                //Thanks team sends empty body if subscription is cancelled and renewal date is passed.
                event = UserSubscriptionStatusEvent.builder().transactionId(txnId).si(si).productPrice(transaction.getAmount()).chargingCycle("UNKNOWN")
                        .subscriptionStartDate(transaction.getExitTime().getTime().toString()).renewalDate((new Date().getTime()) + "").status(CANCELLED_STATE).build();
            }
            eventPublisher.publishEvent(event);
        }
    }
}
