package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class AbstractChargingResponse {
    private String tid;
    private TransactionStatus transactionStatus;

    public Long getExpiry() {
        return System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3);
    }
}
