package in.wynk.payment.service;

import in.wynk.commons.dto.SessionRequest;
import in.wynk.commons.dto.SessionResponse;

public interface ISessionService {

    SessionResponse initSession(SessionRequest request);

}
