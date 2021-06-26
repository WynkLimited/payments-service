package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class TransactionDetails {
    private IAppDetails appDetails;
    private Transaction transaction;
    private IUserDetails userDetails;
}
