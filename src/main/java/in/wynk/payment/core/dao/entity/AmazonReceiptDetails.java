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
public class AmazonReceiptDetails extends ReceiptDetails {

    private String receiptId;
    private String amazonUserId;

}
