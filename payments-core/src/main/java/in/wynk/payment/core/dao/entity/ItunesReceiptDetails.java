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

    public boolean isTransactionIdPresent() {
        return Long.parseLong(getReceiptTransactionId()) > 0L;
    }

    @Override
    public Integer getNotificationType () {
        return null;
    }

    @Override
    public boolean isRenew () {
        return false;
    }
}
