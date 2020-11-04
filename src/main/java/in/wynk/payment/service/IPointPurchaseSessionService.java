package in.wynk.payment.service;

import in.wynk.common.dto.SessionRequest;
import in.wynk.common.dto.SessionResponse;

public interface IPointPurchaseSessionService {

    SessionResponse initSession(SessionRequest request);

}
