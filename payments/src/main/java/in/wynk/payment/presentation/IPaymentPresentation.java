package in.wynk.payment.presentation;

import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.WynkResponseEntity;

/**
 * @author Nishesh Pandey
 */
public interface IPaymentPresentation<R, T> extends IPresentation<WynkResponseEntity<R>, T> { }

