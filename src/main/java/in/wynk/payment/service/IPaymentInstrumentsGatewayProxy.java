package in.wynk.payment.service;

import in.wynk.payment.dto.common.AbstractPaymentInstrumentsProxy;

public interface IPaymentInstrumentsGatewayProxy {
    AbstractPaymentInstrumentsProxy load(String userId);
}
