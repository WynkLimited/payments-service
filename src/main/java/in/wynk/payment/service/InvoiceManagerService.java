package in.wynk.payment.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.lock.WynkRedisLockService;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.InvoiceState;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.event.InvoiceRetryEvent;
import in.wynk.payment.core.service.GSTStateCodesCachingService;
import in.wynk.payment.core.service.InvoiceDetailsCachingService;
import in.wynk.payment.dto.PointDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayProductDetails;
import in.wynk.payment.dto.invoice.*;
import in.wynk.stream.producer.IKafkaEventPublisher;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import in.wynk.vas.client.service.InvoiceVasClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Calendar;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

import static in.wynk.common.constant.BaseConstants.DEFAULT_ACCESS_STATE_CODE;
import static in.wynk.payment.core.constant.PaymentConstants.*;

@Slf4j
@Service(BeanConstant.INVOICE_MANAGER)
@RequiredArgsConstructor
public class InvoiceManagerService implements InvoiceManager {

    @Value("${wynk.kafka.producers.invoice.inform.topic}")
    private String informInvoiceTopic;

    private final ObjectMapper objectMapper;
    private final InvoiceService invoiceService;
    private final InvoiceVasClientService invoiceVasClientService;
    private final ITransactionManagerService transactionManagerService;
    private final PaymentCachingService cachingService;
    private final ITaxManager taxManager;
    private final GSTStateCodesCachingService stateCodesCachingService;
    private final InvoiceDetailsCachingService invoiceDetailsCachingService;
    private final InvoiceNumberGeneratorService invoiceNumberGenerator;
    private final IKafkaEventPublisher<String, InvoiceKafkaMessage> kafkaEventPublisher;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final IPurchaseDetailsManger purchaseDetailsManager;
    private final WynkRedisLockService wynkRedisLockService;
    private final IUserDetailsService userDetailsService;

    @Override
    @ClientAware(clientAlias = "#request.clientAlias")
    public void generate(GenerateInvoiceRequest request) {
        try{
            Lock lock = wynkRedisLockService.getWynkRedisLock(request.getTxnId());
            if (lock.tryLock(3, TimeUnit.SECONDS)) {
                try {
                    final Transaction transaction = transactionManagerService.get(request.getTxnId());
                    final IPurchaseDetails purchaseDetails = purchaseDetailsManager.get(transaction);
                    final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(request.getClientAlias());
                    final MsisdnOperatorDetails operatorDetails = userDetailsService.getOperatorDetails(request.getMsisdn());
                    final String accessStateCode = userDetailsService.getAccessStateCode(operatorDetails, invoiceDetails.getDefaultGSTStateCode(), purchaseDetails);
                    AnalyticService.update(ACCESS_STATE_CODE, accessStateCode);

                    final TaxableRequest taxableRequest = TaxableRequest.builder()
                            .consumerStateCode(accessStateCode).consumerStateName(stateCodesCachingService.get(accessStateCode).getStateName())
                            .supplierStateCode(DEFAULT_ACCESS_STATE_CODE).supplierStateName(stateCodesCachingService.get(DEFAULT_ACCESS_STATE_CODE).getStateName())
                            .amount(transaction.getAmount()).gstPercentage(invoiceDetails.getGstPercentage())
                            .build();
                    AnalyticService.update(TAXABLE_REQUEST, String.valueOf(taxableRequest));
                    final TaxableResponse taxableResponse = taxManager.calculate(taxableRequest);
                    AnalyticService.update(TAXABLE_RESPONSE, String.valueOf(taxableResponse));

                    final String invoiceID = getInvoiceNumber(request.getTxnId(), request.getClientAlias(), request.getType());
                    saveInvoiceDetails(transaction, invoiceID, taxableResponse);
                    publishInvoiceMessage(PublishInvoiceRequest.builder().transaction(transaction).operatorDetails(operatorDetails).purchaseDetails(purchaseDetails)
                            .taxableRequest(taxableRequest).taxableResponse(taxableResponse).invoiceDetails(invoiceDetails).uid(transaction.getUid())
                            .invoiceId(invoiceID).type(request.getType()).build());
                } catch (WynkRuntimeException e) {
                    throw e;
                } finally {
                    lock.unlock();
                }
            } else {
                throw new WynkRuntimeException(PaymentErrorType.PAY455);
            }
        } catch(Exception ex){
            retryInvoiceGeneration(request.getMsisdn(), request.getClientAlias(), request.getTxnId());
            throw new WynkRuntimeException(PaymentErrorType.PAY446, ex);
        }
    }

    @Override
    public void processCallback(InvoiceCallbackRequest request) {
        try{
            AnalyticService.update(INFORM_INVOICE_MESSAGE, objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS).writeValueAsString(request));
            final Invoice invoice = invoiceService.getInvoice(request.getInvoiceId());
            if(invoice.getStatus().equalsIgnoreCase(InvoiceState.SUCCESS.name())){
                return;
            }
            invoice.setUpdatedOn(Calendar.getInstance());
            invoice.setCustomerAccountNumber(request.getCustomerAccountNumber());
            invoice.setDescription(request.getDescription());
            invoice.setStatus(request.getStatus());
            invoice.persisted();
            invoiceService.upsert(invoice);
            final Transaction transaction = transactionManagerService.get(invoice.getTransactionId());
            if(request.getStatus().equalsIgnoreCase(InvoiceState.FAILED.name())){
                retryInvoiceGeneration(transaction.getMsisdn(), transaction.getClientAlias(), invoice.getTransactionId());
            }
        } catch(Exception ex){
            log.error(PaymentLoggingMarker.INVOICE_PROCESS_CALLBACK_FAILED, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY447, ex);
        }
    }

    private String getInvoiceNumber(String txnId, String clientAlias, String type){
        final Invoice invoice = invoiceService.getInvoiceByTransactionId(txnId);
        if(Objects.nonNull(invoice)) return invoice.getId();
        try{
            final String invoiceID = invoiceNumberGenerator.generateInvoiceNumber(clientAlias, type);
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
    private void publishInvoiceMessage (PublishInvoiceRequest request) {
        try {
            String planTitle;
            String offerTitle;
            double amount;
            if (request.getTransaction().getType() == PaymentEvent.POINT_PURCHASE) {
                final IPurchaseDetails purchaseDetails = purchaseDetailsManager.get(request.getTransaction());
                if (purchaseDetails.getProductDetails() instanceof GooglePlayProductDetails) {
                    GooglePlayProductDetails pointDetails = (GooglePlayProductDetails) purchaseDetails.getProductDetails();
                    planTitle = pointDetails.getTitle();
                    offerTitle = pointDetails.getTitle();
                } else {
                    PointDetails pointDetails = (PointDetails) purchaseDetails.getProductDetails();
                    planTitle = pointDetails.getTitle();
                    offerTitle = pointDetails.getTitle();
                }
                amount = request.getTransaction().getAmount();
            } else {
                final PlanDTO plan = cachingService.getPlan(request.getTransaction().getPlanId());
                final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
                planTitle = plan.getTitle();
                offerTitle = offer.getTitle();
                amount = plan.getPrice().getAmount();
            }

            if (request.getType().equalsIgnoreCase(CREDIT_NOTE)) {
                if (Objects.nonNull(request.getTransaction().getOriginalTransactionId())){
                    final Transaction originalTransaction = transactionManagerService.get(request.getTransaction().getOriginalTransactionId());
                    final Invoice originalInvoice = invoiceService.getInvoiceByTransactionId(originalTransaction.getIdStr());
                    final CreditNoteKafkaMessage creditNoteKafkaMessage = CreditNoteKafkaMessage.generateCreditNoteEvent(request,
                            request.getTransaction(), originalInvoice.getId(), originalInvoice.getCreatedOn(), planTitle, amount, offerTitle);
                    AnalyticService.update(INFORM_INVOICE_MESSAGE, objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS).writeValueAsString(creditNoteKafkaMessage));
                    kafkaEventPublisher.publish(informInvoiceTopic, creditNoteKafkaMessage);
                }
            } else {
                final InformInvoiceKafkaMessage informInvoiceKafkaMessage = InformInvoiceKafkaMessage.generateInformInvoiceEvent(request,
                        request.getTransaction(), planTitle, amount, offerTitle);
                AnalyticService.update(INFORM_INVOICE_MESSAGE, objectMapper.setSerializationInclusion(JsonInclude.Include.ALWAYS).writeValueAsString(informInvoiceKafkaMessage));
                kafkaEventPublisher.publish(informInvoiceTopic, informInvoiceKafkaMessage);
            }
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.KAFKA_PUBLISHER_FAILURE, "Unable to publish the inform invoice event in kafka due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY452, e);
        }
    }

    private void saveInvoiceDetails(Transaction transaction, String invoiceID, TaxableResponse taxableResponse){
        final Invoice invoice = invoiceService.getInvoiceByTransactionId(transaction.getIdStr());
        if(Objects.isNull(invoice)) {
            final Invoice.InvoiceBuilder invoiceBuilder = Invoice.builder()
                    .id(invoiceID)
                    .transactionId(transaction.getIdStr())
                    .amount(transaction.getAmount())
                    .taxAmount(taxableResponse.getTaxAmount())
                    .taxableValue(taxableResponse.getTaxableAmount());
            final List<TaxDetailsDTO> list = taxableResponse.getTaxDetails();
            for(TaxDetailsDTO dto : list){
                switch (dto.getTaxType()) {
                    case CGST:
                        invoiceBuilder.cgst(dto.getAmount());
                        break;
                    case SGST:
                        invoiceBuilder.sgst(dto.getAmount());
                        break;
                    case IGST:
                        invoiceBuilder.igst(dto.getAmount());
                        break;
                }
            }
            invoiceBuilder.createdOn(Calendar.getInstance());
            invoiceBuilder.retryCount(0);
            invoiceBuilder.customerAccountNumber(transaction.getUid());
            invoiceBuilder.status(InvoiceState.IN_PROGRESS.name());
            invoiceService.upsert(invoiceBuilder.build());
        }
    }
    @Override
    public CoreInvoiceDownloadResponse download(String txnId) {
        try{
            final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
            final Invoice invoice = invoiceService.getInvoiceByTransactionId(txnId);
            if(Objects.isNull(invoice) || !invoice.getStatus().equalsIgnoreCase(InvoiceState.SUCCESS.name())){
                throw new WynkRuntimeException(PaymentErrorType.PAY442);
            } else {
                final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(clientAlias);
                final byte[] data = invoiceVasClientService.download(invoice.getCustomerAccountNumber(), invoice.getId(), invoiceDetails.getLob());
                return CoreInvoiceDownloadResponse.builder()
                        .invoice(invoice)
                        .data(data)
                        .build();
            }
        } catch(Exception ex){
            log.error(PaymentLoggingMarker.DOWNLOAD_INVOICE_ERROR, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY451, ex);
        }
    }
}
