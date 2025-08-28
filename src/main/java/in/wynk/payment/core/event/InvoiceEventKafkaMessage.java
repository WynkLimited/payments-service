package in.wynk.payment.core.event;

import in.wynk.payment.core.dao.entity.Invoice;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "invoiceEvent")
public class InvoiceEventKafkaMessage {

    private String transactionId;
    private String invoiceExternalId;
    private Double amount;
    private Double taxAmount;
    private Double taxableValue;
    private Double cgst;
    private Double sgst;
    private Double igst;
    private String customerAccountNo;
    private String status;
    private String description;
    private Long createdOn;
    private Long updatedOn;
    private Integer retryCount;
    private String invoiceId;

    public static InvoiceEventKafkaMessage from(InvoiceEvent event) {
        try {
            final Invoice invoice = event.getInvoice();
            return InvoiceEventKafkaMessage.builder()
                    .invoiceId(invoice.getId())
                    .transactionId(invoice.getTransactionId())
                    .invoiceExternalId(invoice.getInvoiceExternalId())
                    .amount(invoice.getAmount())
                    .taxAmount(invoice.getTaxAmount())
                    .taxableValue(invoice.getTaxableValue())
                    .cgst(invoice.getCgst())
                    .sgst(invoice.getSgst())
                    .igst(invoice.getIgst())
                    .customerAccountNo(invoice.getCustomerAccountNumber())
                    .status(invoice.getStatus())
                    .description(invoice.getDescription())
                    .createdOn(invoice.getCreatedOn() != null ? invoice.getCreatedOn().getTimeInMillis() : null)
                    .updatedOn(invoice.getUpdatedOn() != null ? invoice.getUpdatedOn().getTime().getTime() : null)
                    .retryCount(invoice.getRetryCount())
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Error while converting InvoiceEvent to InvoiceEventKafkaMessage", e);
        }
    }

}