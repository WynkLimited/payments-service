package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.PaymentDump;

import java.util.Date;

public interface IPaymentDumpService {
    PaymentDump populatePaymentDump(Date fromDate);
}
