package in.wynk.payment.service;

import in.wynk.common.dto.SessionRequest;
import in.wynk.common.dto.SessionResponse;
import in.wynk.payment.dto.PurchaseRequest;

public interface IPurchaseSessionService {

    SessionResponse initSession(SessionRequest request);

    String init(PurchaseRequest request);


}
