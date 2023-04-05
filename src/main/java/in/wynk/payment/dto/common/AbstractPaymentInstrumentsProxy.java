package in.wynk.payment.dto.common;

import in.wynk.payment.service.IPaymentInstrumentsGatewayService;
import in.wynk.payment.service.IUserPaymentDetails;
import lombok.Builder;
import lombok.Getter;

@Getter
public abstract class AbstractPaymentInstrumentsProxy implements IPaymentInstrumentsGatewayService, IUserPaymentDetails { }
