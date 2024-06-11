package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.ApsPaymentRefundRequest;
import in.wynk.payment.dto.paytm.PaytmPaymentRefundRequest;
import in.wynk.payment.dto.payu.PayUPaymentRefundRequest;
import in.wynk.payment.dto.phonepe.PhonePePaymentRefundRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static in.wynk.payment.constant.WalletConstants.PAYTM_WALLET;
import static in.wynk.payment.constant.WalletConstants.PHONEPE_WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.PAYU;
import static in.wynk.payment.dto.aps.common.ApsConstant.APS;
import static in.wynk.payment.dto.aps.common.ApsConstant.APS_V2;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractPaymentRefundRequest {

    private String reason;
    private String originalTransactionId;

    public static AbstractPaymentRefundRequest from(Transaction originalTransaction, String externalReferenceId, String reason) {
        switch (originalTransaction.getPaymentChannel().getId()) {
            case PAYU:
                return PayUPaymentRefundRequest.from(originalTransaction, externalReferenceId, reason);
            case PAYTM_WALLET:
                return PaytmPaymentRefundRequest.from(originalTransaction, externalReferenceId, reason);
            case PHONEPE_WALLET:
                return PhonePePaymentRefundRequest.from(originalTransaction, externalReferenceId, reason);
            case APS:
                return ApsPaymentRefundRequest.from(originalTransaction, externalReferenceId, reason);
            case APS_V2:
                throw new WynkRuntimeException("Refunds not supported on recharges");
            default:
                throw new WynkRuntimeException("Invalid Payment Channel Id");
        }
    }

}