package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class AbstractChargingResponseV2 {
    private String tid;
    private TransactionStatus transactionStatus;

    public Long getExpiry() {
        return System.currentTimeMillis() + TimeUnit.DAYS.toMillis(3);
    }
}
