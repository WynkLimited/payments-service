package in.wynk.payment.service;

import in.wynk.common.dto.SessionDTO;
import in.wynk.subscription.common.request.SessionRequest;
import in.wynk.payment.dto.IapVerificationRequestV2;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.session.dto.Session;

public interface IDummySessionGenerator {

    IapVerificationRequest initSession(IapVerificationRequest request);

    Session<String, SessionDTO> generate(SessionRequest request);

    IapVerificationRequestV2 initSession(IapVerificationRequestV2 request);
}
