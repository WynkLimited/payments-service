package in.wynk.payment.mapper;

import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.*;

public class DefaultTransactionInitRequestMapper implements IObjectMapper {

    public static AbstractTransactionInitRequest from(AbstractChargingRequest<?> request) {
        if (AbstractChargingRequest.IWebChargingDetails.class.isAssignableFrom(request.getChargingDetails().getClass())) {
            return SessionScopedTransactionInitRequestMapper.from(request);
        } else if (AbstractChargingRequest.IS2SChargingDetails.class.isAssignableFrom(request.getChargingDetails().getClass())) {
            return S2STransactionInitRequestMapper.from(request);
        }
        throw new WynkRuntimeException("Method is not implemented!");
    }

    public static AbstractTransactionInitRequest from(RefundTransactionRequestWrapper wrapper) {
        final Transaction originalTransaction = wrapper.getOriginalTransaction();
        if (originalTransaction.getType() == PaymentEvent.POINT_PURCHASE) {
            return PointTransactionInitRequest.builder().uid(originalTransaction.getUid()).msisdn(originalTransaction.getMsisdn()).amount(originalTransaction.getAmount()).itemId(originalTransaction.getItemId()).clientAlias(originalTransaction.getClientAlias()).paymentCode(originalTransaction.getPaymentChannel()).event(PaymentEvent.REFUND).build();
        } else {
            return PlanTransactionInitRequest.builder().uid(originalTransaction.getUid()).msisdn(originalTransaction.getMsisdn()).amount(originalTransaction.getAmount()).planId(originalTransaction.getPlanId()).clientAlias(originalTransaction.getClientAlias()).paymentCode(originalTransaction.getPaymentChannel()).event(PaymentEvent.REFUND).build();
        }
    }

    public static AbstractTransactionInitRequest from(MigrationTransactionRequest request) {
        return S2STransactionInitRequestMapper.from(request);
    }

}
