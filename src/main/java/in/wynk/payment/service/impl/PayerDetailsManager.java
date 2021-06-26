package in.wynk.payment.service.impl;

import in.wynk.payment.dto.IPayerDetails;
import in.wynk.payment.service.IPayerDetailsManger;
import in.wynk.session.service.ISessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class PayerDetailsManager implements IPayerDetailsManger {

    private final ISessionManager<String, IPayerDetails> sessionManager;

    @Override
    public IPayerDetails save(String transactionId, IPayerDetails details) {
        return sessionManager.init(transactionId, details, 3, TimeUnit.DAYS).getBody();
    }

    @Override
    public IPayerDetails get(String transactionId) {
        return sessionManager.get(transactionId).getBody();
    }
}
