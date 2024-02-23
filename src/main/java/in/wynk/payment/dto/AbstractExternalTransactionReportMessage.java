package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.IAppStoreDetails;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractExternalTransactionReportMessage {
    @Analysed
    private String transactionId;

    @Analysed
    private IAppStoreDetails appStoreDetails;

    @Analysed
    private PaymentEvent paymentEvent;

    @Analysed
    private double amount;

    @Analysed
    private String skuId;

    @NotNull
    @Analysed
    private PaymentGateway paymentGateway;
}
