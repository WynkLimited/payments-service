package in.wynk.payment.dto.invoice;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.List;

@Getter
@SuperBuilder
@AnalysedEntity
@KafkaEvent(topic = "${user.deletion.event.stream.name}")
public class InformInvoiceMessage {

    @Analysed
    private LobInvoice lobInvoice;

    @Getter
    @Builder
    @AnalysedEntity
    public static class LobInvoice {
        @Analysed
        @Field("EMAIL")
        private boolean email;
        @Analysed
        @Field("LOB")
        private String lob;
        @Analysed
        @Field("SMS")
        private boolean sms;
        @Analysed
        private CustomerDetails customerDetails;
        @Analysed
        private CustomerInvoiceDetails customerInvoiceDetails;
        @Analysed
        @Field("customerRechargeRate")
        private List<CustomerRechargeRate> customerRechargeRates;
        @Analysed
        private TaxDetails taxDetails;

        @Getter
        @Builder
        @AnalysedEntity
        public static class CustomerDetails {
            @Analysed
            @Field("KCINumber")
            private long  kciNumber;
            @Analysed
            private String address;
            @Analysed
            private long alternateNumber;
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
            @Field("PaymentTransactionID")
            private String paymentTransactionId;
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
}