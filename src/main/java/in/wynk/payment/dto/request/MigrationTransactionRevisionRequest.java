package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Calendar;

@Getter
@SuperBuilder
@AnalysedEntity
public class MigrationTransactionRevisionRequest extends AbstractTransactionRevisionRequest {
    @Analysed
    private final Calendar nextChargingDate;
}