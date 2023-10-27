package in.wynk.payment.utils;

import in.wynk.payment.core.dao.entity.InvoiceDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.InvoiceDetailsCachingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class TaxUtils {

    private final InvoiceDetailsCachingService invoiceDetailsCachingService;

    public double calculateTax(Transaction transaction) {
        final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(transaction.getClientAlias());
        final double totalTaxAmount = getTotalTaxAmount(transaction.getAmount(), invoiceDetails.getGstPercentage());
        return totalTaxAmount;
    }

    private double getTotalTaxAmount (double amount, double gstPercentage){
        return Math.round(amount * gstPercentage) / 100.0;
    }
}
