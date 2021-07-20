package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.CustomerWindBackRequest;

public interface ICustomerWinBackService {

    WynkResponseEntity<Void> winBack(CustomerWindBackRequest request);

}
