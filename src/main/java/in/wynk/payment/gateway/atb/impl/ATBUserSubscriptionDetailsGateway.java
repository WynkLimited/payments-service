package in.wynk.payment.gateway.atb.impl;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.UserSubscriptionStatusEvent;
import in.wynk.payment.gateway.atb.ATBUserSubscriptionService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.vas.client.dto.atb.UserSubscriptionStatusResponse;
import in.wynk.vas.client.service.CatalogueVasClientService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Date;
import java.util.Objects;
import java.util.Optional;

import static in.wynk.common.constant.BaseConstants.CANCELLED_STATE;

/**
 * @author Nishesh Pandey
 */
@Service
@RequiredArgsConstructor
public class ATBUserSubscriptionDetailsGateway implements ATBUserSubscriptionService {
    private final CatalogueVasClientService catalogueVasClientService;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManagerService;
    private final PaymentCachingService cachingService;

    public UserSubscriptionStatusResponse getUserSubscriptionDetails (String si, String txnId) {
        Optional<UserSubscriptionStatusResponse> response = Optional.empty();
        Transaction transaction = transactionManagerService.get(txnId);
        PlanDTO selectedPlan = cachingService.getPlan(transaction.getPlanId());
        String productCode = selectedPlan.getSku().get("atb");
        try {
            ResponseEntity<UserSubscriptionStatusResponse[]> userSubscriptionStatusResponseResponseEntity = catalogueVasClientService.getUserSubscriptionStatusResponse(si);
            if (Objects.requireNonNull(userSubscriptionStatusResponseResponseEntity.getBody()).length > 0) {
                response = Arrays.stream(userSubscriptionStatusResponseResponseEntity.getBody())
                        .filter(userSubscriptionStatusResponse -> userSubscriptionStatusResponse.getProductCode().equalsIgnoreCase(productCode)).findFirst();
                if (response.isPresent()) {
                    return response.get();
                }
            }
            return UserSubscriptionStatusResponse.builder().si(si).subscriptionStatus(CANCELLED_STATE).build();
        } catch (Exception e) {
            throw new RuntimeException("Exception occurred while finding user subscription status from thanks for the si: " + si, e);
        } finally {
            UserSubscriptionStatusEvent event;
            if (response.isPresent()) {
                UserSubscriptionStatusResponse userSubscriptionStatusResponse = response.get();
                event = UserSubscriptionStatusEvent.builder().transactionId(txnId).si(si).productCode(userSubscriptionStatusResponse.getProductCode())
                        .productPrice(userSubscriptionStatusResponse.getProductPrice())
                        .chargingCycle(userSubscriptionStatusResponse.getChargingCycle()).subscriptionStartDate(userSubscriptionStatusResponse.getPeriodStartDate())
                        .renewalDate(userSubscriptionStatusResponse.getRenewalDate())
                        .status(userSubscriptionStatusResponse.getSubscriptionStatus()).build();
            } else {
                //Thanks team sends empty body if subscription is cancelled and renewal date is passed.
                event = UserSubscriptionStatusEvent.builder().transactionId(txnId).si(si).productCode(productCode).productPrice(transaction.getAmount())
                        .chargingCycle(selectedPlan.getPeriod().getValidityUnit())
                        .subscriptionStartDate(transaction.getExitTime().getTime().toString()).renewalDate((new Date().getTime()) + "").status(CANCELLED_STATE).build();
            }
            eventPublisher.publishEvent(event);
        }
    }
}
