package in.wynk.payment.service;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.InvoiceSequence;
import in.wynk.payment.core.dao.repository.InvoiceSequenceDao;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

import static in.wynk.payment.core.constant.PaymentConstants.CREDIT_NOTE;

@Slf4j
@Service(BeanConstant.INVOICE_SEQUENCE_GENERATOR)
@RequiredArgsConstructor
public class InvoiceNumberGeneratorService {

    private final InvoiceSequenceDao invoiceSequenceDao;
    private final RedissonClient redissonClient;

    public String generateInvoiceNumber(String clientAlias, String type){
        final String monthYear = getCurrentMonthYear();
        final String sequence = getNextSequenceValue(clientAlias, type);
        return PaymentConstants.INVOICE_SEQUENCE_PREFIX + monthYear + sequence;
    }

    private String getNextSequenceValue(String clientAlias, String type) {
        final RLock lock = redissonClient.getLock(PaymentConstants.INVOICE_SEQUENCE_LOCK_KEY);
        lock.lock();
        try {
            final InvoiceSequence sequence = invoiceSequenceDao.findById(clientAlias).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY453));
            if (type.equalsIgnoreCase(CREDIT_NOTE)){
                final long seqNumber = sequence.getCreditNote().getCnSequence() + 1;
                sequence.getCreditNote().setCnSequence(seqNumber);
                invoiceSequenceDao.save(sequence);
                return sequence.getCreditNote().getCnIdentifier() + seqNumber;
            } else {
                final long seqNumber = sequence.getSequenceNumber() + 1;
                sequence.setSequenceNumber(seqNumber);
                invoiceSequenceDao.save(sequence);
                return sequence.getIdentifier() + seqNumber;
            }
        } finally {
            lock.unlock();
        }
    }

    private String getCurrentMonthYear(){
        final LocalDate today = LocalDate.now();
        final int month = today.getMonthValue();
        final String monthStr = (month < 10)? "0" + month : String.valueOf(month);
        final String year = String.valueOf(today.getYear()).substring(2,4);
        return monthStr + year;
    }
}
