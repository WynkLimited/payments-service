package in.wynk.payment.service;

import in.wynk.commons.dto.SessionRequest;
import in.wynk.commons.dto.SessionResponse;

public interface IPointPurchaseSessionService {

    SessionResponse initSession(SessionRequest request);

}
