package in.wynk.payment.dto.common;

import in.wynk.payment.service.IPaymentInstrumentsGatewayService;
import in.wynk.payment.service.IUserPaymentDetails;
import lombok.Getter;

@Getter
public abstract class AbstractPaymentInstrumentsProxy<T extends AbstractPaymentOptionInfo, K extends AbstractSavedInstrumentInfo> implements IPaymentInstrumentsGatewayService<T>, IUserPaymentDetails<K> { }
