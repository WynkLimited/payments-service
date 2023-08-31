package in.wynk.payment.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.InvoiceState;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.event.GenerateInvoiceEvent;
import in.wynk.payment.core.event.InvoiceEvent;
import in.wynk.payment.core.event.InvoiceRetryEvent;
import in.wynk.payment.core.service.GSTStateCodesCachingService;
import in.wynk.payment.core.service.InvoiceDetailsCachingService;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.invoice.*;
import in.wynk.stream.producer.IKafkaEventPublisher;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import in.wynk.vas.client.service.InvoiceVasClientService;
import in.wynk.vas.client.service.VasClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import java.util.*;

import static in.wynk.common.constant.BaseConstants.DEFAULT_GST_STATE_CODE;
import static in.wynk.payment.core.constant.PaymentConstants.*;

@Slf4j
@Service(BeanConstant.INVOICE_MANAGER)
@RequiredArgsConstructor
public class InvoiceManagerService implements InvoiceManager {

    @Value("${wynk.kafka.producers.invoice.inform.topic}")
    private String informInvoiceTopic;

    private final ObjectMapper objectMapper;
    private final InvoiceService invoiceService;
    private final VasClientService vasClientService;
    private final InvoiceVasClientService invoiceVasClientService;
    private final ITransactionManagerService transactionManagerService;
    private final PaymentCachingService cachingService;
    private final ITaxManager taxManager;
    private final GSTStateCodesCachingService stateCodesCachingService;
    private final InvoiceDetailsCachingService invoiceDetailsCachingService;
    private final InvoiceNumberGeneratorService invoiceNumberGenerator;
    private final IKafkaEventPublisher<String, String> kafkaEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @TransactionAware(txnId = "#request.txnId")
    @ClientAware(clientAlias = "#request.clientAlias")
    public void generate(GenerateInvoiceRequest request) {
        try{
            final Transaction transaction = TransactionContext.get();
            final IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().orElseThrow(() -> new WynkRuntimeException("Purchase details is not found"));
            final MsisdnOperatorDetails operatorDetails = getOperatorDetails(request.getMsisdn());
            final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(request.getClientAlias());
            final String accessStateCode = getAccessStateCode(operatorDetails, invoiceDetails, purchaseDetails);

            final TaxableRequest taxableRequest = TaxableRequest.builder()
                    .consumerStateCode(accessStateCode).consumerStateName(stateCodesCachingService.get(accessStateCode).getStateName())
                    .supplierStateCode(DEFAULT_GST_STATE_CODE).supplierStateName(stateCodesCachingService.get(DEFAULT_GST_STATE_CODE).getStateName())
                    .amount(transaction.getAmount()).gstPercentage(invoiceDetails.getGstPercentage())
                    .build();
            AnalyticService.update(taxableRequest.toString());
            final TaxableResponse taxableResponse = taxManager.calculate(taxableRequest);
            AnalyticService.update(taxableResponse.toString());

            final String invoiceID = getInvoiceNumber(request.getTxnId(), request.getClientAlias());
            saveInvoiceDetails(transaction, invoiceID, taxableResponse);
            publishInvoiceMessage(operatorDetails, purchaseDetails, taxableResponse, invoiceDetails, invoiceID);
        } catch(Exception ex){
            retryInvoiceGeneration(request.getMsisdn(), request.getClientAlias(), request.getTxnId());
            throw new WynkRuntimeException(PaymentErrorType.PAY446, ex);
        }
    }

    @Override
    public void processCallback(InvoiceCallbackRequest request) {
        try{
            final Invoice invoice = invoiceService.getInvoice(request.getInvoiceId()).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY445));
            invoice.setUpdatedOn(Calendar.getInstance());
            invoice.setCustomerAccountNumber(request.getCustomerAccountNumber());
            invoice.setDescription(request.getDescription());
            invoice.setStatus(request.getStatus());
            invoice.setUpdatedOn(request.getUpdatedOn());
            invoiceService.upsert(invoice);
            invoice.persisted();
            final Transaction transaction = transactionManagerService.get(invoice.getTransactionId());
            final InvoiceEvent invoiceEvent = InvoiceEvent.from(invoice, transaction.getClientAlias());
            applicationEventPublisher.publishEvent(invoiceEvent);

            if(request.getStatus().equalsIgnoreCase("FAILED")){
                retryInvoiceGeneration(transaction.getMsisdn(), transaction.getClientAlias(), invoice.getTransactionId());
            }
        } catch(Exception ex){
            log.error(PaymentLoggingMarker.INVOICE_PROCESS_CALLBACK_FAILED, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY447, ex);
        }
    }

    private String getInvoiceNumber(String txnId, String clientAlias){
        final Invoice invoice = invoiceService.getInvoiceByTransactionId(txnId).orElse(null);
        if(Objects.nonNull(invoice)) return invoice.getId();
        try{
            final String invoiceID = invoiceNumberGenerator.generateInvoiceNumber(clientAlias);
            AnalyticService.update(invoiceID);
            return invoiceID;
        } catch(Exception e){
            log.error(PaymentLoggingMarker.INVOICE_NUMBER_GENERATION_FAILED, "Unable to generate the invoice number due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY454, e);
        }
    }

    private void retryInvoiceGeneration (String msisdn, String clientAlias, String txnId) {
        final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(clientAlias);
        final List<Long> retries = invoiceDetails.getRetries();
        if(Objects.nonNull(retries)){
            applicationEventPublisher.publishEvent(InvoiceRetryEvent.builder()
                    .msisdn(msisdn)
                    .clientAlias(clientAlias)
                    .txnId(txnId)
                    .retries(retries).build());
        } else {
            log.info(PaymentLoggingMarker.INVOICE_RETRIES_NOT_CONFIGURED, "Won't retry invoice generation as retries not configured for the client");
        }
    }

    @AnalyseTransaction(name = "publishInvoiceKafka")
    private void publishInvoiceMessage(MsisdnOperatorDetails operatorDetails, IPurchaseDetails purchaseDetails, TaxableResponse taxableResponse, InvoiceDetails invoiceDetails, String invoiceNumber){
        try{
            final Transaction transaction = TransactionContext.get();
            final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
            String stateName = null;
            if(Objects.nonNull(purchaseDetails.getGeoLocation()) && Objects.nonNull(purchaseDetails.getGeoLocation().getStateCode())){
                stateName = stateCodesCachingService.getByISOStateCode(purchaseDetails.getGeoLocation().getStateCode()).getStateName();
            }
            final InformInvoiceKafkaMessage informInvoiceKafkaMessage = InformInvoiceKafkaMessage.generateInformInvoiceEvent(operatorDetails, taxableResponse, invoiceDetails, transaction, invoiceNumber, plan, stateName);
            final String informInvoiceKafkaMessageStr = objectMapper.writeValueAsString(informInvoiceKafkaMessage);
            AnalyticService.update(INFORM_INVOICE_MESSAGE, informInvoiceKafkaMessageStr);
            //AnalyticService.update(informInvoiceKafkaMessage);
            kafkaEventPublisher.publish(informInvoiceTopic, informInvoiceKafkaMessageStr);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.KAFKA_PUBLISHER_FAILURE, "Unable to publish the inform invoice event in kafka due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY452, e);
        }
    }

    private void saveInvoiceDetails(Transaction transaction, String invoiceID, TaxableResponse taxableResponse){
        final Invoice invoice = invoiceService.getInvoiceByTransactionId(transaction.getIdStr()).orElse(null);
        if(Objects.nonNull(invoice)) {
            invoice.persisted();
            InvoiceEvent invoiceEvent = InvoiceEvent.from(invoice, transaction.getClientAlias());
            applicationEventPublisher.publishEvent(invoiceEvent);
            return;
        }
        final InvoiceEvent.InvoiceEventBuilder builder = InvoiceEvent.builder()
                .clientAlias(transaction.getClientAlias())
                .invoiceId(invoiceID)
                .transactionId(transaction.getIdStr())
                .amount(transaction.getAmount())
                .taxAmount(taxableResponse.getTaxAmount())
                .taxableValue(taxableResponse.getTaxableAmount());
        final List<TaxDetailsDTO> list = taxableResponse.getTaxDetails();
        for(TaxDetailsDTO dto : list){
            switch (dto.getTaxType()) {
                case CGST:
                    builder.cgst(dto.getAmount());
                    break;
                case SGST:
                    builder.sgst(dto.getAmount());
                    break;
                case IGST:
                    builder.igst(dto.getAmount());
                    break;
            }
        }
        builder.state(InvoiceState.IN_PROGRESS.name());
        builder.retryCount(0);
        builder.createdOn(Calendar.getInstance().getTimeInMillis());
        builder.updatedOn(Calendar.getInstance().getTimeInMillis());
        applicationEventPublisher.publishEvent(builder.build());
    }
    @Override
    @TransactionAware(txnId = "#tid")
    public byte[] download(String txnId) {
        final Transaction transaction = TransactionContext.get();
        //final Transaction transaction = transactionManagerService.get(txnId);
        //final PurchaseDetails purchaseDetails =RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IPurchasingDetailsDao.class).findById(txnId).orElse(null);
        //final IPurchaseDetails purchaseDetails = purchaseDetailsManger.get(transaction);
        final Optional<Invoice> invoiceOptional = invoiceService.getInvoiceByTransactionId(txnId);
        if(!invoiceOptional.isPresent()){
            applicationEventPublisher.publishEvent(GenerateInvoiceEvent.builder()
                    .msisdn(transaction.getMsisdn())
                    .txnId(txnId)
                    .clientAlias(transaction.getClientAlias())
                    .build());
            throw new WynkRuntimeException(PaymentErrorType.PAY445);
        }
        try{
            final Invoice invoice = invoiceOptional.get();
            if(invoice.getStatus().equalsIgnoreCase("FAILED")){
                applicationEventPublisher.publishEvent(GenerateInvoiceEvent.builder()
                        .msisdn(transaction.getMsisdn())
                        .txnId(txnId)
                        //.transaction(TransactionDTO.from(transaction))
                        //.purchaseDetails(purchaseDetails)
                        .clientAlias(transaction.getClientAlias())
                        .build());
                throw new WynkRuntimeException(PaymentErrorType.PAY446);
                //todo : handle failure exceptions
            }
            //final byte[] data = invoiceVasClientService.download(invoice.getCustomerAccountNumber(), invoice.getId(), invoice.getLob());
            /*return CoreInvoiceDownloadResponse.builder()
                    .invoice(invoice)
                    .data(data)
                    .build();*/
            final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(transaction.getClientAlias());
            return invoiceVasClientService.download(invoice.getCustomerAccountNumber(), invoice.getId(), invoiceDetails.getLob());
        } catch(Exception ex){
            log.error(PaymentLoggingMarker.DOWNLOAD_INVOICE_ERROR, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY451, ex);
        }
    }

    private MsisdnOperatorDetails getOperatorDetails(String msisdn) {
        try {
            if (StringUtils.isNotBlank(msisdn)) {
                MsisdnOperatorDetails operatorDetails = vasClientService.allOperatorDetails(msisdn);
                if (Objects.nonNull(operatorDetails)) {
                    return operatorDetails;
                }
            }
            return null;
        } catch (Exception ex) {
            log.error(PaymentLoggingMarker.OPERATOR_DETAILS_NOT_FOUND, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY443, ex);
        }
    }

    private String getAccessStateCode(MsisdnOperatorDetails operatorDetails, InvoiceDetails invoiceDetails, IPurchaseDetails purchaseDetails) {
        String gstStateCode = (Strings.isNullOrEmpty(invoiceDetails.getDefaultGSTStateCode())) ? DEFAULT_GST_STATE_CODE: invoiceDetails.getDefaultGSTStateCode();
        try {
            if (Objects.nonNull(operatorDetails) &&
                    Objects.nonNull(operatorDetails.getUserMobilityInfo()) &&
                    Objects.nonNull(operatorDetails.getUserMobilityInfo().getGstStateCode())) {
                gstStateCode = operatorDetails.getUserMobilityInfo().getGstStateCode().trim();
            } else {
                if(Objects.nonNull(purchaseDetails) &&
                        Objects.nonNull(purchaseDetails.getGeoLocation()) &&
                        Objects.nonNull(purchaseDetails.getGeoLocation().getStateCode())){
                    gstStateCode = stateCodesCachingService.getByISOStateCode(purchaseDetails.getGeoLocation().getStateCode()).getId();
                }
            }
        } catch (Exception ex) {
            log.error(PaymentLoggingMarker.GST_STATE_CODE_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY441, ex);
        }
        return gstStateCode;
    }
}
