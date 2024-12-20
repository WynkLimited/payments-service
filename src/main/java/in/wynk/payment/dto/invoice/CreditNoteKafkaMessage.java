package in.wynk.payment.dto.invoice;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import com.google.common.base.Strings;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.InvoiceDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.InvoiceService;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import in.wynk.vas.client.dto.UserMobilityInfo;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Objects;


@Getter
@SuperBuilder
@AnalysedEntity
@RequiredArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class CreditNoteKafkaMessage extends InvoiceKafkaMessage {

    @Analysed
    @JsonProperty("LOB")
    private String lob;
    @Analysed
    @JsonProperty("TYPE")
    private String type;

    @Analysed
    private CreditNoteKafkaMessage.CustomerDetails customerDetails;
    @Analysed
    private CreditNoteKafkaMessage.CustomerInvoiceDetails customerInvoiceDetails;
    @Analysed
    @JsonProperty("customerRechargeRate")
    private List<CreditNoteKafkaMessage.CustomerRechargeRate> customerRechargeRates;
    @Analysed
    private CreditNoteKafkaMessage.TaxDetails taxDetails;

    @Getter
    @Builder
    @AnalysedEntity
    public static class CustomerDetails {
        @Analysed
        @JsonProperty("KCINumber")
        private String kciNumber;
        @Analysed
        private String customerAccountNo;
        @Analysed
        private String stateCode;
        @Analysed
        private String stateName;
    }

    @Getter
    @Builder
    @AnalysedEntity
    public static class CustomerInvoiceDetails {
        @Analysed
        @JsonProperty("paymentTransactionID")
        private String paymentTransactionID;
        @Analysed
        private double invoiceAmount;
        @Analysed
        private String invoiceDate;
        @Analysed
        private String invoiceNumber;
        @Analysed
        private String paymentDate;
        @Analysed
        private String paymentMode;
        @Analysed
        private String typeOfService;
        @Analysed
        private double discount;
        @Analysed
        private double discountedPrice;
        @Analysed
        private String cnInvoiceDate;
        @Analysed
        private String cnInvoiceNumber;
    }

    @Getter
    @Builder
    @AnalysedEntity
    public static class CustomerRechargeRate {
        @Analysed
        private String category;
        @Analysed
        private String hsnCodeNo;
        @Analysed
        private double rate;
        @Analysed
        private int unit;
    }

    @Getter
    @Builder
    @AnalysedEntity
    public static class TaxDetails {
        @Analysed
        private String taxableValue;
        @Analysed
        private List<CreditNoteKafkaMessage.TaxDetails.SubRow> subRow;
        @Analysed
        private String taxAmount;

        @Getter
        @Builder
        @AnalysedEntity
        public static class SubRow {
            @Analysed
            private String taxType;
            @Analysed
            private String rate;
            @Analysed
            private String amount;
        }
    }

    public static CreditNoteKafkaMessage generateCreditNoteEvent(PublishInvoiceRequest request, Transaction transaction, String originalInvoiceId, Calendar originalInvoiceDate, String planTitle, double amount, String offerTitle){
        final CreditNoteKafkaMessage.CustomerDetails customerDetails = generateCustomerDetails(request.getOperatorDetails(), request.getTaxableRequest(), transaction.getMsisdn(),
                request.getUid());
        final CreditNoteKafkaMessage.CustomerInvoiceDetails customerInvoiceDetails = generateCustomerInvoiceDetails(request.getTaxableResponse(), transaction, originalInvoiceId, originalInvoiceDate, request.getInvoiceId(), offerTitle, amount,
                request.getInvoiceDetails(), request.getPurchaseDetails());
        final List<CreditNoteKafkaMessage.CustomerRechargeRate> customerRechargeRates = generateCustomerRechargeRate(request.getTaxableResponse(), request.getInvoiceDetails(), planTitle);
        final CreditNoteKafkaMessage.TaxDetails taxDetails = generateTaxDetails(request.getTaxableResponse());

        return CreditNoteKafkaMessage.builder()
                .lob(request.getInvoiceDetails().getLob())
                .customerDetails(customerDetails)
                .customerInvoiceDetails(customerInvoiceDetails)
                .customerRechargeRates(customerRechargeRates)
                .taxDetails(taxDetails)
                .type("WYNKCN")
                .build();

    }

    private static CreditNoteKafkaMessage.CustomerInvoiceDetails generateCustomerInvoiceDetails(TaxableResponse taxableResponse, Transaction transaction, String originalInvoiceId, Calendar originalInvoiceDate, String invoiceNumber, String title, double amount, InvoiceDetails invoiceDetails, IPurchaseDetails purchaseDetails) {
        String paymentMode = null;
        if(Objects.nonNull(purchaseDetails) && Objects.nonNull(purchaseDetails.getPaymentDetails()) && Objects.nonNull(purchaseDetails.getPaymentDetails().getPaymentMode())){
            paymentMode = purchaseDetails.getPaymentDetails().getPaymentMode();
        }
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");

        return CustomerInvoiceDetails.builder()
                .invoiceDate(ZonedDateTime.ofInstant(originalInvoiceDate.toInstant(), originalInvoiceDate.getTimeZone().toZoneId()).format(formatter))
                .cnInvoiceNumber(invoiceNumber)
                .cnInvoiceDate(LocalDateTime.now().format(formatter))
                .paymentTransactionID(transaction.getIdStr())
                .invoiceNumber(originalInvoiceId)
                .invoiceAmount(amount)
                .paymentDate(transaction.getInitTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(formatter))
                .paymentMode(invoiceDetails.getPaymentModes().getOrDefault(paymentMode, invoiceDetails.getPaymentModes().get(BaseConstants.DEFAULT)))
                .typeOfService(title)
                .discount(amount - transaction.getAmount())
                .discountedPrice(transaction.getAmount())
                .build();
    }

    private static String sanitize(String value){
        if(Strings.isNullOrEmpty(value)){
            return "";
        } else if(value.contains("null")){
            return value.replaceAll("null","").trim();
        }
        return value.trim();
    }

    private static CreditNoteKafkaMessage.CustomerDetails generateCustomerDetails(MsisdnOperatorDetails operatorDetails, TaxableRequest taxableRequest, String msisdn, String uid) {
        final String stateCode = taxableRequest.getConsumerStateCode();
        final String state = taxableRequest.getConsumerStateName();
        final CreditNoteKafkaMessage.CustomerDetails.CustomerDetailsBuilder customerDetailsBuilder = CreditNoteKafkaMessage.CustomerDetails.builder();
        if(Objects.nonNull(operatorDetails) && Objects.nonNull(operatorDetails.getUserMobilityInfo())){
            final UserMobilityInfo userMobilityInfo = operatorDetails.getUserMobilityInfo();
            final String name = (Strings.isNullOrEmpty(userMobilityInfo.getMiddleName()))?
                    sanitize(userMobilityInfo.getFirstName() + " " +userMobilityInfo.getLastName()) :
                    sanitize(userMobilityInfo.getFirstName() + " " +userMobilityInfo.getMiddleName() + " " +userMobilityInfo.getLastName());
            final String address = (Strings.isNullOrEmpty(userMobilityInfo.getResDistrict()))?
                    sanitize(userMobilityInfo.getResCity() + " " +userMobilityInfo.getResState()) :
                    sanitize(userMobilityInfo.getResCity() + " " +userMobilityInfo.getResDistrict() + " " +userMobilityInfo.getResState());
            final String kciNumber = sanitize(msisdn.replace("+91", ""));
            /*final String customerAccountNo = (Strings.isNullOrEmpty(userMobilityInfo.getCustomerID()))? msisdn.replace("+91", "") : sanitize(userMobilityInfo.getCustomerID());*/
            final String customerAccountNo = sanitize(uid);
            return customerDetailsBuilder.stateCode(stateCode).stateName(state)
                    .kciNumber(kciNumber)
                    .customerAccountNo(customerAccountNo).build();
        }
        return customerDetailsBuilder.kciNumber(sanitize(msisdn.replace("+91", ""))).customerAccountNo(uid).stateCode(stateCode).stateName(state)
                .build();
    }

    private static List<CreditNoteKafkaMessage.CustomerRechargeRate> generateCustomerRechargeRate(TaxableResponse taxableResponse, InvoiceDetails invoiceDetails, String title) {
        final List<CreditNoteKafkaMessage.CustomerRechargeRate> customerRechargeRatesList = new ArrayList<>();
        customerRechargeRatesList.add(CustomerRechargeRate.builder()
                .rate(taxableResponse.getTaxableAmount())
                .hsnCodeNo(invoiceDetails.getSACCode())
                .category((Objects.isNull(title))? PaymentConstants.INVOICE_CATEGORY : title)
                .unit(1)
                .build());
        return customerRechargeRatesList;
    }
    private static CreditNoteKafkaMessage.TaxDetails generateTaxDetails(TaxableResponse taxableResponse) {
        final List<CreditNoteKafkaMessage.TaxDetails.SubRow> taxDetailsList = new ArrayList<>();
        for(TaxDetailsDTO dto : taxableResponse.getTaxDetails()){
            taxDetailsList.add(CreditNoteKafkaMessage.TaxDetails.SubRow.builder()
                    .amount(String.valueOf(dto.getAmount()))
                    .taxType(dto.getTaxType().getType())
                    .rate(String.valueOf(dto.getRate()))
                    .build());
        }
        return CreditNoteKafkaMessage.TaxDetails.builder()
                .taxAmount(String.valueOf(taxableResponse.getTaxAmount()))
                .taxableValue(String.valueOf(taxableResponse.getTaxableAmount()))
                .subRow(taxDetailsList)
                .build();
    }
}