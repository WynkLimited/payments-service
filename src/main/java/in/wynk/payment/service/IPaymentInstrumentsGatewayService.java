package in.wynk.payment.service;


import in.wynk.payment.dto.common.AbstractPaymentOptionInfo;

import java.util.List;

public interface IPaymentInstrumentsGatewayService<T extends AbstractPaymentOptionInfo> {
    List<T> getPaymentInstruments(String userId);
}
