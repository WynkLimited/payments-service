package in.wynk.payment.dto.gpbs.acknowledge.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class GooglePlayReportExternalTransactionRequest extends AbstractPaymentAcknowledgementRequest {
    @Analysed
    private Transaction transaction;

    @Analysed
    private IPurchaseDetails purchaseDetails;

    @Analysed
    private String externalTransactionToken;

    @Analysed
    private String clientAlias;

    @Analysed
    private final String paymentCode;

    @Override
    public String getPaymentCode () {
        return this.paymentCode;
    }

    @Override
    public String getType () {
        return BaseConstants.PLAN;
    }

    @Override
    public String getTxnId () {
        return this.transaction.getIdStr();
    }
}
