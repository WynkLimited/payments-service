package in.wynk.payment.service;

import in.wynk.payment.dto.PayerDetails;

public interface IPayerDetailsManger {

    PayerDetails save(String transactionId, PayerDetails details);

    PayerDetails get(String transactionId);

}
