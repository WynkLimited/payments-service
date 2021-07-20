package in.wynk.payment.service.impl;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.CustomerWindBackRequest;
import in.wynk.payment.service.ICustomerWinBackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerWinBackServiceImpl implements ICustomerWinBackService {

    @Override
    public WynkResponseEntity<Void> winBack(CustomerWindBackRequest request) {
        return null;
    }
}
