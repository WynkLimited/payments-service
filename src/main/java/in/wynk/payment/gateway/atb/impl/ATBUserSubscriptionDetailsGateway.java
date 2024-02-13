package in.wynk.payment.gateway.atb.impl;

import in.wynk.payment.core.event.UserSubscriptionStatusEvent;
import in.wynk.payment.gateway.atb.ATBUserSubscriptionService;
import in.wynk.vas.client.dto.atb.UserSubscriptionStatusResponse;
import in.wynk.vas.client.service.CatalogueVasClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author Nishesh Pandey
 */
@Service
public class ATBUserSubscriptionDetailsGateway implements ATBUserSubscriptionService {

    @Autowired
    private CatalogueVasClientService catalogueVasClientService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    public UserSubscriptionStatusResponse getUserSubscriptionDetails (String si) {
        try {
            ResponseEntity<UserSubscriptionStatusResponse> data = catalogueVasClientService.getUserSubscriptionStatusResponse(si);
            eventPublisher.publishEvent(UserSubscriptionStatusEvent.builder().status(Objects.requireNonNull(data.getBody()).getSuccess()).si(si));
            return data.getBody();
        } catch (Exception e) {
            throw new RuntimeException("Exception occurred while finding user subscription status from thanks for the si: " + si, e);
        }
    }
}
