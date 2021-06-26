package in.wynk.payment.service.impl;

import in.wynk.payment.dto.ICombinedUserDetails;
import in.wynk.payment.service.IUserDetailsManger;
import in.wynk.session.service.ISessionManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class UserDetailsManager implements IUserDetailsManger {

    private final ISessionManager<String, ICombinedUserDetails> sessionManager;

    @Override
    public ICombinedUserDetails save(String transactionId, ICombinedUserDetails details) {
        return sessionManager.init(transactionId, details, 3, TimeUnit.DAYS).getBody();
    }

    @Override
    public ICombinedUserDetails get(String transactionId) {
        return sessionManager.get(transactionId).getBody();
    }
}
