package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.common.response.PaymentStatusWrapper;

/**
 * @author Nishesh Pandey
 */
public interface IPaymentStatus<R extends PaymentStatusWrapper> {

    R status (Transaction transaction);
}
