package in.wynk.payment.service;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.repository.InvoiceSequenceDao;
import in.wynk.payment.dto.invoice.GenerateInvoiceRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service(BeanConstant.INVOICE_SEQUENCE_GENERATOR)
@RequiredArgsConstructor
public class InvoiceNumberGeneratorService {

    private final InvoiceSequenceDao invoiceSequenceDao;

    public String generateInvoiceNo(GenerateInvoiceRequest request){
        request.getTransaction();
        return "To be Generated";
    }
}
