package in.wynk.payment.dto.invoice;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import com.google.common.base.Strings;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.InvoiceDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import in.wynk.vas.client.dto.UserMobilityInfo;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Getter
@SuperBuilder
@AnalysedEntity
@RequiredArgsConstructor
public class InformInvoiceKafkaMessage extends InvoiceKafkaMessage {

    @Analysed
    private LobInvoice lobInvoice;

    @Getter
    @Builder
    @AnalysedEntity
    public static class LobInvoice {
        @Analysed
        @JsonProperty("EMAIL")
        private boolean email;
        @Analysed
        @JsonProperty("LOB")
        private String lob;
        @Analysed
        @JsonProperty("skip_delivery")
        private String skip_delivery;
        @Analysed
        @JsonProperty("SMS")
        private boolean sms;
        @Analysed
        private CustomerDetails customerDetails;
        @Analysed
        private CustomerInvoiceDetails customerInvoiceDetails;
        @Analysed
        @JsonProperty("customerRechargeRate")
        private List<CustomerRechargeRate> customerRechargeRates;
        @Analysed
        private TaxDetails taxDetails;

        @Getter
        @Builder
        @AnalysedEntity
        public static class CustomerDetails {
            @Analysed
            @JsonProperty("KCINumber")
            private String kciNumber;
            @Analysed
            private String address;
            @Analysed
            private String alternateNumber;
            @Analysed
            private String customerAccountNo;
            @Analysed
            private String customerClassification;
            @Analysed
            private String customerType;
            @Analysed
            private String emailId;
            @Analysed
            private String gstn;
            @Analysed
            private String name;
            @Analysed
            private String panNumber;
            @Analysed
            private String pinCode;
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
            /*@Analysed
            private String qrCode;*/
            @Analysed
            private double discount;
            @Analysed
            private double discountedPrice;
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
            private List<SubRow> subRow;
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
    }

        public static InformInvoiceKafkaMessage generateInformInvoiceEvent(PublishInvoiceRequest request, Transaction transaction, String planTitle, double amount, String offerTitle, String skip_delivery){
        final InformInvoiceKafkaMessage.LobInvoice.CustomerDetails customerDetails = generateCustomerDetails(request.getOperatorDetails(), request.getTaxableRequest(), transaction.getMsisdn(),
                request.getUid());
        final InformInvoiceKafkaMessage.LobInvoice.CustomerInvoiceDetails customerInvoiceDetails = generateCustomerInvoiceDetails(request.getTaxableResponse(), transaction, request.getInvoiceId(), offerTitle, amount,
                request.getInvoiceDetails(), request.getPurchaseDetails());
        final List<InformInvoiceKafkaMessage.LobInvoice.CustomerRechargeRate> customerRechargeRates = generateCustomerRechargeRate(request.getTaxableResponse(), request.getInvoiceDetails(), planTitle);
        final InformInvoiceKafkaMessage.LobInvoice.TaxDetails taxDetails = generateTaxDetails(request.getTaxableResponse());
        final boolean sendEmail = Objects.nonNull(request.getOperatorDetails()) &&
                Objects.nonNull(request.getOperatorDetails().getUserMobilityInfo()) &&
                Objects.nonNull(request.getOperatorDetails().getUserMobilityInfo().getEmailID());
        return InformInvoiceKafkaMessage.builder()
                .lobInvoice(LobInvoice.builder()
                        .lob(request.getInvoiceDetails().getLob())
                        .sms(true)
                        .email(sendEmail)
                        .skip_delivery(skip_delivery)
                        .customerDetails(customerDetails)
                        .customerInvoiceDetails(customerInvoiceDetails)
                        .customerRechargeRates(customerRechargeRates)
                        .taxDetails(taxDetails)
                        .build())
                .build();
    }

    private static InformInvoiceKafkaMessage.LobInvoice.CustomerInvoiceDetails generateCustomerInvoiceDetails(TaxableResponse taxableResponse, Transaction transaction, Transaction originalTransaction, String invoiceNumber, String title, double amount, InvoiceDetails invoiceDetails, IPurchaseDetails purchaseDetails) {
        /*double CGST = 0.0;
        double SGST = 0.0;
        double IGST = 0.0;
        for(TaxDetailsDTO dto : taxableResponse.getTaxDetails()){
            switch (dto.getTaxType()) {
                case CGST:
                    CGST = dto.getAmount();
                case SGST:
                    SGST = dto.getAmount();
                case IGST:
                    IGST = dto.getAmount();
            }
        }*/
        String paymentMode = null;
        if(Objects.nonNull(purchaseDetails) && Objects.nonNull(purchaseDetails.getPaymentDetails()) && Objects.nonNull(purchaseDetails.getPaymentDetails().getPaymentMode())){
            paymentMode = purchaseDetails.getPaymentDetails().getPaymentMode();
        }
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm:ss");
        return LobInvoice.CustomerInvoiceDetails.builder()
                .invoiceDate(transaction.getUpdatedAt().format(formatter))
                .paymentTransactionID(transaction.getIdStr())
                .invoiceNumber(invoiceNumber)
                .invoiceAmount(amount)
                .paymentDate(transaction.getInitTime().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime().format(formatter))
                .paymentMode(invoiceDetails.getPaymentModes().getOrDefault(paymentMode, invoiceDetails.getPaymentModes().get(BaseConstants.DEFAULT)))
                .typeOfService(title)
                .discount(amount - transaction.getAmount())
                .discountedPrice(transaction.getAmount())
                /*.qrCode("upi://pay?" +
                        "pa=21000037032.FL@mairtel&" +
                        "pn=Bharti%20Airtel%20Ltd.&" +
                        "mc=4814&" +
                        "tr=HT2307I000000747&" +
                        "tn=102-Telemedia-BAL&" +
                        "cu=INR&" +
                        "am=0&" +
                        "mam=0&" +
                        "mode=01&" +
                        "purpose=00&" +
                        "orgId=000000," +
                        "InvNo="+invoiceNumber+"," +
                        "InvoiceBilldate="+ LocalDate.now()+"," +
                        "ItemCount=1," +
                        "HSN/SACCode="+invoiceDetails.getSACCode()+"," +
                        "SellerGST="+invoiceDetails.getGSTRegistrationNo()+"," +
                        "Payee%20Bank%20AcctNo=," +
                        "IFSC_CODE=," +
                        "Tot_GST="+taxableResponse.getTaxAmount()+"," +
                        "CGST="+CGST+"," +
                        "SGST="+SGST+"," +
                        "IGST="+IGST+"," +
                        "CESS=0.0," +
                        "TotalInvAmt="+taxableResponse.getTaxableAmount())*/
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

    private static InformInvoiceKafkaMessage.LobInvoice.CustomerDetails generateCustomerDetails(MsisdnOperatorDetails operatorDetails, TaxableRequest taxableRequest, String msisdn, String uid) {
        final String stateCode = taxableRequest.getConsumerStateCode();
        final String state = taxableRequest.getConsumerStateName();
        final InformInvoiceKafkaMessage.LobInvoice.CustomerDetails.CustomerDetailsBuilder customerDetailsBuilder = InformInvoiceKafkaMessage.LobInvoice.CustomerDetails.builder();
        if(Objects.nonNull(operatorDetails) && Objects.nonNull(operatorDetails.getUserMobilityInfo())){
            final UserMobilityInfo userMobilityInfo = operatorDetails.getUserMobilityInfo();
            final String name = (Strings.isNullOrEmpty(userMobilityInfo.getMiddleName()))?
                    sanitize(userMobilityInfo.getFirstName() + " " +userMobilityInfo.getLastName()) :
                    sanitize(userMobilityInfo.getFirstName() + " " +userMobilityInfo.getMiddleName() + " " +userMobilityInfo.getLastName());
            final String address = (Strings.isNullOrEmpty(userMobilityInfo.getResDistrict()))?
                    sanitize(userMobilityInfo.getResCity() + " " +userMobilityInfo.getResState()) :
                    sanitize(userMobilityInfo.getResCity() + " " +userMobilityInfo.getResDistrict() + " " +userMobilityInfo.getResState());
            final String pinCode = sanitize(userMobilityInfo.getResPinCode());
            final String alternateNumber = sanitize(userMobilityInfo.getAlternateContactNumber());
            final String kciNumber = sanitize(msisdn.replace("+91", ""));
            final String emailId = sanitize(userMobilityInfo.getEmailID());
            final String gstNumber = sanitize(userMobilityInfo.getGstNumber());
            final String panNumber = sanitize(userMobilityInfo.getPanNumber());
            final String customerType = sanitize(userMobilityInfo.getCustomerType());
            final String customerClassification = sanitize(userMobilityInfo.getCustomerClassification());
            /*final String customerAccountNo = (Strings.isNullOrEmpty(userMobilityInfo.getCustomerID()))? msisdn.replace("+91", "") : sanitize(userMobilityInfo.getCustomerID());*/
            final String customerAccountNo = sanitize(uid);
            return customerDetailsBuilder.name(name).address(address).pinCode(pinCode).stateCode(stateCode).stateName(state)
                    .alternateNumber(alternateNumber).kciNumber(kciNumber).emailId(emailId).gstn(gstNumber).panNumber(panNumber)
                    .customerType(customerType).customerClassification(customerClassification).customerAccountNo(customerAccountNo).build();
        }
        return customerDetailsBuilder.kciNumber(sanitize(msisdn.replace("+91", ""))).customerAccountNo(uid).stateCode(stateCode).stateName(state)
                .name(PaymentConstants.BLANK).address(PaymentConstants.BLANK).pinCode(PaymentConstants.BLANK).alternateNumber(PaymentConstants.BLANK)
                .emailId(PaymentConstants.BLANK).gstn(PaymentConstants.BLANK).panNumber(PaymentConstants.BLANK).customerType(PaymentConstants.BLANK).customerClassification(PaymentConstants.BLANK).build();
    }

    private static List<InformInvoiceKafkaMessage.LobInvoice.CustomerRechargeRate> generateCustomerRechargeRate(TaxableResponse taxableResponse, InvoiceDetails invoiceDetails, String title) {
        final List<InformInvoiceKafkaMessage.LobInvoice.CustomerRechargeRate> customerRechargeRatesList = new ArrayList<>();
        customerRechargeRatesList.add(LobInvoice.CustomerRechargeRate.builder()
                .rate(taxableResponse.getTaxableAmount())
                .hsnCodeNo(invoiceDetails.getSACCode())
                .category((Objects.isNull(title))? PaymentConstants.INVOICE_CATEGORY : title)
                .unit(1)
                .build());
        return customerRechargeRatesList;
    }
    private static InformInvoiceKafkaMessage.LobInvoice.TaxDetails generateTaxDetails(TaxableResponse taxableResponse) {
        final List<InformInvoiceKafkaMessage.LobInvoice.TaxDetails.SubRow> taxDetailsList = new ArrayList<>();
        for(TaxDetailsDTO dto : taxableResponse.getTaxDetails()){
            taxDetailsList.add(InformInvoiceKafkaMessage.LobInvoice.TaxDetails.SubRow.builder()
                    .amount(String.valueOf(dto.getAmount()))
                    .taxType(dto.getTaxType().getType())
                    .rate(String.valueOf(dto.getRate()))
                    .build());
        }
        return InformInvoiceKafkaMessage.LobInvoice.TaxDetails.builder()
                .taxAmount(String.valueOf(taxableResponse.getTaxAmount()))
                .taxableValue(String.valueOf(taxableResponse.getTaxableAmount()))
                .subRow(taxDetailsList)
                .build();
    }
}