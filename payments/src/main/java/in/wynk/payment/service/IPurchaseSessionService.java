package in.wynk.payment.service;

import in.wynk.common.dto.SessionResponse;
import in.wynk.payment.dto.BestValuePlanPurchaseRequest;
import in.wynk.payment.dto.PurchaseRequest;
import in.wynk.subscription.common.request.SessionRequest;

import java.util.Map;

public interface IPurchaseSessionService {

    SessionResponse initSession (SessionRequest request);

    String init (PurchaseRequest request);

    String init (BestValuePlanPurchaseRequest request, Map<String, String> additionalParam);
}
