package in.wynk.payment.core.dao.entity;

import lombok.*;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@ToString
@AllArgsConstructor
public class ItunesReceiptDetails extends ReceiptDetails {

    private String receipt;
    private String type;

}
