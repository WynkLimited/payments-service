package in.wynk.payment.service;

import in.wynk.payment.dto.request.IapVerificationRequest;

public interface IDummySessionGenerator {

    IapVerificationRequest initSession(IapVerificationRequest request);

}
