package in.wynk.payment.gateway;

import in.wynk.payment.dto.request.AbstractRechargeOrderRequest;
import in.wynk.payment.dto.response.AbstractRechargeOrderResponse;

/**
 * @author Nishesh Pandey
 */
public interface IRechargeOrder<R extends AbstractRechargeOrderResponse, T extends AbstractRechargeOrderRequest> {
    R order(T request);
}

