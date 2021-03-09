package in.wynk.payment.core.dao.entity;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class ItunesReceiptDetails extends ReceiptDetails {

    private String receipt;
    private String type;
    private long transactionId = -1;

    public boolean isTransactionIdPresent() {
        return transactionId > 0L;
    }

}
