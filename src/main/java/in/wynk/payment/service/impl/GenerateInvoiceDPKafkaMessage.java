package in.wynk.payment.service.impl;

import in.wynk.payment.dto.invoice.CallbackInvoiceKafkaMessage;
import in.wynk.payment.dto.invoice.GenerateInvoiceKafkaMessage;
import in.wynk.payment.dto.invoice.InvoiceKafkaMessage;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import static in.wynk.logging.BaseLoggingMarkers.KAFKA_MESSAGE_CREATOR_ERROR;

@Getter
@Builder
@Slf4j
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "generateInvoice")
public class GenerateInvoiceDPKafkaMessage {
    private String msisdn;
    private String clientAlias;
    private String txnId;
    private String type;
    private String skip_delivery;
    private String lob;
    private String customerAccountNumber;
    private String invoiceId;
    private String status;
    private String description;

    public static GenerateInvoiceDPKafkaMessage from(InvoiceKafkaMessage request) {
        try {
            if(request instanceof GenerateInvoiceKafkaMessage) {
                final GenerateInvoiceKafkaMessage generateInvoiceKafkaMessage = (GenerateInvoiceKafkaMessage) request;
                return GenerateInvoiceDPKafkaMessage.builder()
                        .txnId(generateInvoiceKafkaMessage.getTxnId())
                        .type(generateInvoiceKafkaMessage.getType())
                        .skip_delivery(generateInvoiceKafkaMessage.getType())
                        .msisdn(generateInvoiceKafkaMessage.getMsisdn())
                        .build();
            } else if(request instanceof CallbackInvoiceKafkaMessage) {
                final CallbackInvoiceKafkaMessage callbackInvoiceKafkaMessage = (CallbackInvoiceKafkaMessage) request;
                return GenerateInvoiceDPKafkaMessage.builder()
                        .type(callbackInvoiceKafkaMessage.getType())
                        .skip_delivery(callbackInvoiceKafkaMessage.getType())
                        .lob(callbackInvoiceKafkaMessage.getLob())
                        .invoiceId(callbackInvoiceKafkaMessage.getInvoiceId())
                        .customerAccountNumber(callbackInvoiceKafkaMessage.getCustomerAccountNumber())
                        .description(callbackInvoiceKafkaMessage.getDescription())
                        .status(callbackInvoiceKafkaMessage.getStatus())
                        .build();
            }
        } catch(Exception ex) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR,"Error in creating GenerateInvoiceKafkaMessage for request: {}", request, ex);
        }
        return null;
    }
}
