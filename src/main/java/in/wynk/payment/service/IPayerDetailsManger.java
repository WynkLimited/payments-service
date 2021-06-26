package in.wynk.payment.service;

import in.wynk.payment.dto.IPayerDetails;

public interface IPayerDetailsManger {

    IPayerDetails save(String transactionId, IPayerDetails details);

    IPayerDetails get(String transactionId);

}
