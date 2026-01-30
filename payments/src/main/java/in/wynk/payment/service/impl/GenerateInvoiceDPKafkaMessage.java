package in.wynk.payment.service.impl;

import in.wynk.payment.dto.invoice.*;
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
    private String optimusGstStateCode;
    private String geoLocationGstStateCode;
    private String defaultGstStateCode;

    public static GenerateInvoiceDPKafkaMessage from(GenerateInvoiceRequest request, GstStateCode gstStateCode) {
        try {
            return GenerateInvoiceDPKafkaMessage.builder()
                    .txnId(request.getTxnId())
                    .type(request.getType())
                    .skip_delivery(request.getSkipDelivery())
                    .msisdn(request.getMsisdn())
                    .defaultGstStateCode(gstStateCode != null ? gstStateCode.getDefaultGstStateCode() : null)
                    .geoLocationGstStateCode(gstStateCode != null ? gstStateCode.getGeoLocationGstStateCode() : null)
                    .optimusGstStateCode(gstStateCode != null ? gstStateCode.getOptimusGstStateCode() : null)
                    .build();
        } catch (Exception ex) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating GenerateInvoiceKafkaMessage for request: {}", request, ex);
        }
        return null;
    }

    public static GenerateInvoiceDPKafkaMessage from(InvoiceKafkaMessage request) {
        try {
            if(request instanceof GenerateInvoiceKafkaMessage) {
                final GenerateInvoiceKafkaMessage generateInvoiceKafkaMessage = (GenerateInvoiceKafkaMessage) request;
                return GenerateInvoiceDPKafkaMessage.builder()
                        .txnId(generateInvoiceKafkaMessage.getTxnId())
                        .type(generateInvoiceKafkaMessage.getType())
                        .skip_delivery(generateInvoiceKafkaMessage.getSkip_delivery())
                        .msisdn(generateInvoiceKafkaMessage.getMsisdn())
                        .build();
            } else if(request instanceof CallbackInvoiceKafkaMessage) {
                final CallbackInvoiceKafkaMessage callbackInvoiceKafkaMessage = (CallbackInvoiceKafkaMessage) request;
                return GenerateInvoiceDPKafkaMessage.builder()
                        .type(callbackInvoiceKafkaMessage.getType())
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
