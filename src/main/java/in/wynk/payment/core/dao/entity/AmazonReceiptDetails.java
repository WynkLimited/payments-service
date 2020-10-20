package in.wynk.payment.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@ToString
@AllArgsConstructor
public class AmazonReceiptDetails extends ReceiptDetails {

    private String receiptId;

}
