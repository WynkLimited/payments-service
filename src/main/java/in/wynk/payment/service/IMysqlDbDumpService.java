package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.PaymentDbDump;

import java.util.Date;

public interface IMysqlDbDumpService {
    PaymentDbDump populatePaymentDbDump(Date fromDate, Date toDate);
}
