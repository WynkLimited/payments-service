package in.wynk.payment.service;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.common.base.Strings;
import com.google.gson.Gson;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.BeanConstant;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.*;

import static in.wynk.common.constant.BaseConstants.DEFAULT_GST_STATE_CODE;
import static in.wynk.payment.core.constant.PaymentConstants.*;

@Slf4j
@Service(BeanConstant.INVOICE_MANAGER)
@RequiredArgsConstructor
public class InvoiceManagerService implements InvoiceManager {

    private final Gson gson;
    private final InvoiceService invoiceService;
    private final VasClientService vasClientService;
    private final InvoiceVasClientService invoiceVasClientService;
    private final ITransactionManagerService transactionManagerService;
    private final PaymentCachingService cachingService;
    private final ITaxManager taxManager;
    private final GSTStateCodesCachingService stateCodesCachingService;
    private final InvoiceDetailsCachingService invoiceDetailsCachingService;
    private final InvoiceNumberGeneratorService invoiceNumberGenerator;
    private final IKafkaEventPublisher<String, InvoiceKafkaMessage> kafkaEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Override
    @TransactionAware(txnId = "#request.txnId")
    @ClientAware(clientAlias = "#request.clientAlias")
    public void generate(GenerateInvoiceRequest request) {
        try{
            final Transaction transaction = TransactionContext.get();
            final MsisdnOperatorDetails operatorDetails = getOperatorDetails(request.getMsisdn());
            final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(request.getClientAlias());
            final String accessStateCode = getAccessStateCode(operatorDetails, invoiceDetails);

            TaxableRequest taxableRequest = TaxableRequest.builder()
                    .consumerStateCode(accessStateCode)
                    .supplierStateCode(DEFAULT_GST_STATE_CODE)
                    .consumerStateName(stateCodesCachingService.get(accessStateCode).getStateName())
                    .supplierStateName(stateCodesCachingService.get(DEFAULT_GST_STATE_CODE).getStateName())
                    .amount(transaction.getAmount())
                    .gstPercentage(invoiceDetails.getGstPercentage())
                    .build();
            AnalyticService.update(TAXABLE_REQUEST, taxableRequest.toString());
            final TaxableResponse taxableResponse = taxManager.calculate(taxableRequest);
            AnalyticService.update(TAXABLE_RESPONSE, taxableResponse.toString());

            if(Objects.isNull(request.getInvoiceId())){
                final String invoiceID = invoiceNumberGenerator.generateInvoiceNumber(request.getClientAlias());
                //publish invoice message to kafka
                publishInvoiceMessage(operatorDetails, taxableResponse, invoiceDetails, request, invoiceID);
                //save invoice details in DB
                saveInvoiceDetails(transaction, invoiceID, taxableResponse);
            } else {
                final Invoice invoice = invoiceService.getInvoice(request.getInvoiceId()).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY445));
                //publish invoice message to kafka
                publishInvoiceMessage(operatorDetails, taxableResponse, invoiceDetails, request, request.getInvoiceId());
                //update the retry count
                invoice.setRetryCount(invoice.getRetryCount() + 1);
                invoiceService.upsert(invoice);
            }
        } catch(Exception ex){
            scheduleInvoiceGenerationRetry(request.getInvoiceId(), request.getMsisdn(), request.getClientAlias(), request.getTxnId());
            throw new WynkRuntimeException(PaymentErrorType.PAY446, ex);
        }
    }

    private void scheduleInvoiceGenerationRetry(String invoiceId, String msisdn, String clientAlias, String txnId) {
        final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(clientAlias);
        final List<Long> retries = invoiceDetails.getRetries();
        if(Objects.nonNull(retries)){
            applicationEventPublisher.publishEvent(InvoiceRetryEvent.builder()
                    .invoiceId(invoiceId)
                    .msisdn(msisdn)
                    .clientAlias(clientAlias)
                    .txnId(txnId)
                    .retries(retries).build());
        } else {
            log.info(PaymentLoggingMarker.INVOICE_RETRIES_NOT_CONFIGURED, "Won't retry invoice generation as retries not configured for the client");
        }
    }

    @AnalyseTransaction(name = "publishInvoiceKafka")
    private void publishInvoiceMessage(MsisdnOperatorDetails operatorDetails, TaxableResponse taxableResponse, InvoiceDetails invoiceDetails, GenerateInvoiceRequest request, String invoiceNumber){
        try{
            final Transaction transaction = TransactionContext.get();
            final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
            final InformInvoiceKafkaMessage informInvoiceKafkaMessage = InformInvoiceKafkaMessage.generateInformInvoiceEvent(operatorDetails, taxableResponse, invoiceDetails, transaction, invoiceNumber, plan);
            AnalyticService.update(INFORM_INVOICE_MESSAGE, gson.toJson(informInvoiceKafkaMessage));
            AnalyticService.update(informInvoiceKafkaMessage);
            kafkaEventPublisher.publish(informInvoiceKafkaMessage);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.KAFKA_PUBLISHER_FAILURE, "Unable to publish the inform invoice event in kafka due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY452, e);
        }
    }

    private void saveInvoiceDetails(Transaction transaction, String invoiceNumber, TaxableResponse taxableResponse){
        final InvoiceEvent.InvoiceEventBuilder builder = InvoiceEvent.builder()
                .clientAlias(transaction.getClientAlias())
                .invoiceId(invoiceNumber)
                .transactionId(transaction.getIdStr())
                .amount(transaction.getAmount())
                .taxAmount(taxableResponse.getTaxAmount())
                .taxableValue(taxableResponse.getTaxableAmount())
                .retryCount(0);
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
        builder.createdOn(Calendar.getInstance());
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
                    //.transaction(TransactionDTO.from(transaction))
                    //.purchaseDetails(purchaseDetails)
                    .clientAlias(transaction.getClientAlias())
                    .build());
            throw new WynkRuntimeException(PaymentErrorType.PAY445);
        }
        try{
            final Invoice invoice = invoiceOptional.get();
            if(!invoice.getStatus().equalsIgnoreCase("SUCCESS")){
                applicationEventPublisher.publishEvent(GenerateInvoiceEvent.builder()
                        .invoiceId(invoice.getId())
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

    @Override
    public void processCallback(InvoiceCallbackRequest request) {
        try{
            final Invoice invoice = invoiceService.getInvoice(request.getInvoiceId()).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY445));
            invoice.setUpdatedOn(Calendar.getInstance());
            invoice.setCustomerAccountNumber(request.getCustomerAccountNumber());
            invoice.setDescription(request.getDescription());
            invoice.setStatus(request.getStatus());
            invoice.setRetryCount(invoice.getRetryCount());
            invoiceService.upsert(invoice);

            if(!request.getStatus().equalsIgnoreCase("SUCCESS")){
                final Transaction transaction = transactionManagerService.get(invoice.getTransactionId());
                scheduleInvoiceGenerationRetry(request.getInvoiceId(), transaction.getMsisdn(), transaction.getClientAlias(), transaction.getIdStr());
            }
        } catch(Exception ex){
            log.error(PaymentLoggingMarker.INVOICE_PROCESS_CALLBACK_FAILED, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY447, ex);
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

    private String getAccessStateCode(MsisdnOperatorDetails operatorDetails, InvoiceDetails invoiceDetails) {
        String gstStateCode = (Strings.isNullOrEmpty(invoiceDetails.getDefaultGSTStateCode())) ? DEFAULT_GST_STATE_CODE: invoiceDetails.getDefaultGSTStateCode();
        try {
            if (Objects.nonNull(operatorDetails) &&
                    Objects.nonNull(operatorDetails.getUserMobilityInfo()) &&
                    Objects.nonNull(operatorDetails.getUserMobilityInfo().getGstStateCode())) {
                gstStateCode = operatorDetails.getUserMobilityInfo().getGstStateCode().trim();
            } else {
                final IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().orElseThrow(() -> new WynkRuntimeException("Purchase details is not found"));
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
