package in.wynk.payment.service;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.InvoiceTaxType;
import in.wynk.payment.dto.invoice.TaxDetailsDTO;
import in.wynk.payment.dto.invoice.TaxableRequest;
import in.wynk.payment.dto.invoice.TaxableResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service(BeanConstant.TAX_MANAGER)
@RequiredArgsConstructor
public class TaxManagerService implements ITaxManager {

    @Override
    public TaxableResponse calculate(TaxableRequest request) {
        final double totalTaxAmount = getTotalTaxAmount(request.getAmount(), request.getGstPercentage());
        final List<TaxDetailsDTO> taxDetailsList = new ArrayList<>();
        if(request.getConsumerStateCode().equalsIgnoreCase(request.getSupplierStateCode())){
            final double halfTaxAmount = Math.round((totalTaxAmount / 2) * 100.0) / 100.0;
            final double halfTaxRate = request.getGstPercentage() / 2;
            taxDetailsList.add(TaxDetailsDTO.builder()
                    .amount(halfTaxAmount)
                    .rate(halfTaxRate)
                    .taxType(InvoiceTaxType.CGST).build());
            taxDetailsList.add(TaxDetailsDTO.builder()
                    .amount(halfTaxAmount)
                    .rate(halfTaxRate)
                    .taxType(InvoiceTaxType.SGST).build());
            taxDetailsList.add(TaxDetailsDTO.builder()
                    .amount(0)
                    .rate(0)
                    .taxType(InvoiceTaxType.IGST).build());
            return TaxableResponse.builder()
                    .taxAmount(Math.round((totalTaxAmount) * 100.0) / 100.0)
                    .taxableAmount(Math.round((request.getAmount() - totalTaxAmount) * 100.0) / 100.0)
                    .taxDetails(taxDetailsList).build();
        }
        taxDetailsList.add(TaxDetailsDTO.builder()
                .amount(0)
                .rate(0)
                .taxType(InvoiceTaxType.CGST).build());
        taxDetailsList.add(TaxDetailsDTO.builder()
                .amount(0)
                .rate(0)
                .taxType(InvoiceTaxType.SGST).build());
        taxDetailsList.add(TaxDetailsDTO.builder()
                .amount(Math.round((totalTaxAmount) * 100.0) / 100.0)
                .rate(request.getGstPercentage())
                .taxType(InvoiceTaxType.IGST).build());
        return TaxableResponse.builder()
                .taxAmount(Math.round((totalTaxAmount) * 100.0) / 100.0)
                .taxableAmount(Math.round((request.getAmount() - totalTaxAmount) * 100.0) / 100.0)
                .taxDetails(taxDetailsList).build();
    }

    private double getTotalTaxAmount (double amount, double gstPercentage){
        //return Math.round(amount * gstPercentage) / 100.0;
        /** Logic -
         *   consider plan amount as 100, and GST percentage as 18%
         *
         *   Total Tax = 100 - (100 / (18 + 100) * 100) = 100 - 84.745762711 = 15.254237289
         *
         * **/
        return amount - ((amount * 100.0)/(gstPercentage + 100.0));
    }
}
