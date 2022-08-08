package in.wynk.payment.dto.request;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.PaymentCode;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TransactionrateRequest {

        private String key;
        private String command;
        private String var1;
        private String hash;
}
