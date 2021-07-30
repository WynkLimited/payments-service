package in.wynk.payment.dto.request;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PointTransactionInitRequest extends AbstractTransactionInitRequest {

    private String itemId;

}

