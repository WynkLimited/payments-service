package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.IAppDetails;
import in.wynk.payment.dto.IUserDetails;
import in.wynk.payment.dto.request.AbstractTransactionInitRequest;
import in.wynk.payment.dto.request.AbstractTransactionRevisionRequest;

public interface ITransactionManagerService {

    Transaction get(String id);

   Transaction init(AbstractTransactionInitRequest transactionInitRequest);

   Transaction init(AbstractTransactionInitRequest transactionInitRequest, IUserDetails userDetails, IAppDetails appDetails);

    void revision(AbstractTransactionRevisionRequest request);

}
