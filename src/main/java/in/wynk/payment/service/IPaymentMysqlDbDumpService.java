package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.PaymentMysqlDbDump;

import java.util.Date;

public interface IPaymentMysqlDbDumpService {
    PaymentMysqlDbDump populatePaymentDbDump(Date fromDate);
}
