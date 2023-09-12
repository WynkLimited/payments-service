package in.wynk.payment.service;

import in.wynk.payment.dto.invoice.TaxableRequest;
import in.wynk.payment.dto.invoice.TaxableResponse;

public interface ITaxManager {
    TaxableResponse calculate(TaxableRequest request);
}
