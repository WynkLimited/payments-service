package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.dto.AbstractPack;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public abstract class AbstractChargingStatusResponse {
    private String tid;
    private int planId;
    private AbstractPack packDetails;
    private TransactionStatus transactionStatus;
}
