package in.wynk.payment.validations;

import in.wynk.common.validations.IBaseRequest;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.dto.response.LatestReceiptResponse;

public interface IReceiptValidatorRequest<T extends LatestReceiptResponse> extends IBaseRequest {

    T getLatestReceiptInfo();

    PaymentGateway getPaymentCode();

}
