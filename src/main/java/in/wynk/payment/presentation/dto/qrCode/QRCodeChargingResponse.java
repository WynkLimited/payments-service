package in.wynk.payment.presentation.dto.qrCode;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class QRCodeChargingResponse {
    private String tid;
    private TransactionStatus transactionStatus;
    private final String transactionType;
    private String action;
}