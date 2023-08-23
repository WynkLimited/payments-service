package in.wynk.payment.service;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.advice.TimeIt;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Invoice;
import in.wynk.payment.core.dao.entity.InvoiceDetails;
import in.wynk.payment.core.dao.repository.InvoiceDao;
import in.wynk.payment.core.service.GSTStateCodesCachingService;
import in.wynk.payment.core.service.InvoiceDetailsCachingService;
import in.wynk.payment.dto.invoice.*;
import in.wynk.stream.producer.IEventPublisher;
import in.wynk.vas.client.dto.MsisdnOperatorDetails;
import in.wynk.vas.client.service.VasClientService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;
import static in.wynk.common.constant.BaseConstants.DEFAULT_GST_STATE_CODE;
import static in.wynk.common.constant.BaseConstants.UNKNOWN_GST_STATE_CODE;

@Slf4j
@Service(BeanConstant.INVOICE_MANAGER)
@RequiredArgsConstructor
public class InvoiceManagerService implements InvoiceManager {

    private final InvoiceDao invoiceDao;
    private final VasClientService vasClientService;
    private final ITaxManager taxManager;
    private final GSTStateCodesCachingService stateCodesCachingService;
    private final InvoiceDetailsCachingService invoiceDetailsCachingService;
    private final InvoiceNumberGeneratorService invoiceNumberGenerator;
    private final IEventPublisher<InformInvoiceMessage> eventPublisher;

    @Override
    public void generate(GenerateInvoiceRequest request) {
        log.info("generate invoice request received");
        AnalyticService.update(request);
        final MsisdnOperatorDetails operatorDetails = getOperatorDetails(request.getMsisdn());
        final String accessStateCode = getAccessStateCode(operatorDetails);
        final InvoiceDetails invoiceDetails = invoiceDetailsCachingService.get(request.getTransaction().getClientAlias());
        if(Objects.nonNull(invoiceDetails)){
            TaxableRequest taxableRequest = TaxableRequest.builder()
                    .consumerStateCode(accessStateCode)
                    .supplierStateCode(DEFAULT_GST_STATE_CODE)
                    .consumerStateName(stateCodesCachingService.get(accessStateCode).getStateName())
                    .supplierStateName(stateCodesCachingService.get(DEFAULT_GST_STATE_CODE).getStateName())
                    .amount(request.getTransaction().getAmount())
                    .gstPercentage(invoiceDetails.getGstPercentage())
                    .build();
            AnalyticService.update(taxableRequest);
            final TaxableResponse taxableResponse = taxManager.calculate(taxableRequest);
            AnalyticService.update(taxableResponse);
            final InformInvoiceMessage informInvoiceMessage = generateInformInvoiceEvent(operatorDetails, taxableResponse, invoiceDetails, request);
            AnalyticService.update(informInvoiceMessage);
            eventPublisher.publish(informInvoiceMessage);
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY442);
    }

    @Override
    public byte[] download (String txnId) {
        return new byte[0];
    }

    @Override
    @TimeIt
    @Transactional(readOnly = true, timeout = 1)
    @Cacheable(cacheName = "Invoice", cacheKey = "'id:'+ #invoiceId", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public Invoice getInvoice(String invoiceId) {
        return invoiceDao.findById(invoiceId).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY442));
    }

    @Override
    @TimeIt
    @Transactional(rollbackFor = Exception.class, timeout = 1)
    @CacheEvict(cacheName = "Invoice", cacheKey = "'id:'+ #invoice.getId()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public void save(Invoice invoice) {
        invoiceDao.save(invoice);
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

    private String getAccessStateCode(MsisdnOperatorDetails operatorDetails) {
        String gstStateCode = UNKNOWN_GST_STATE_CODE;
        try {
            if (Objects.nonNull(operatorDetails) &&
                    Objects.nonNull(operatorDetails.getUserMobilityInfo()) &&
                    Objects.nonNull(operatorDetails.getUserMobilityInfo().getGstStateCode())) {
                gstStateCode = operatorDetails.getUserMobilityInfo().getGstStateCode().trim();
            }
        } catch (Exception ex) {
            log.error(PaymentLoggingMarker.GST_STATE_CODE_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PaymentErrorType.PAY441, ex);
        }
        return gstStateCode;
    }

    private InformInvoiceMessage generateInformInvoiceEvent(MsisdnOperatorDetails operatorDetails, TaxableResponse taxableResponse, InvoiceDetails invoiceDetails, GenerateInvoiceRequest request){
        final InformInvoiceMessage.LobInvoice.CustomerDetails customerDetails = generateCustomerDetails(operatorDetails);
        final InformInvoiceMessage.LobInvoice.CustomerInvoiceDetails customerInvoiceDetails = generateCustomerInvoiceDetails(request);
        final List<InformInvoiceMessage.LobInvoice.CustomerRechargeRate> customerRechargeRates = generateCustomerRechargeRate(taxableResponse, invoiceDetails);
        final InformInvoiceMessage.LobInvoice.TaxDetails taxDetails = generateTaxDetails(taxableResponse);
        return InformInvoiceMessage.builder()
                .lobInvoice(InformInvoiceMessage.LobInvoice.builder()
                        .customerDetails(customerDetails)
                        .customerInvoiceDetails(customerInvoiceDetails)
                        .customerRechargeRates(customerRechargeRates)
                        .taxDetails(taxDetails)
                        .build())
                .build();
    }

    private InformInvoiceMessage.LobInvoice.CustomerInvoiceDetails generateCustomerInvoiceDetails (GenerateInvoiceRequest request) {
        final String invoiceNumber = invoiceNumberGenerator.generateInvoiceNo(request);
        return InformInvoiceMessage.LobInvoice.CustomerInvoiceDetails.builder()
                .invoiceDate(new Date().toString())
                .paymentTransactionId(request.getTransaction().getIdStr())
                .invoiceNumber(invoiceNumber)
                .invoiceAmount(request.getTransaction().getAmount())
                .paymentDate(request.getTransaction().getInitTime().toString())
                .paymentMode(request.getTransaction().getPaymentChannel().getCode())
                .build();
    }

    private InformInvoiceMessage.LobInvoice.CustomerDetails generateCustomerDetails (MsisdnOperatorDetails operatorDetails) {
        final InformInvoiceMessage.LobInvoice.CustomerDetails.CustomerDetailsBuilder customerDetailsBuilder = InformInvoiceMessage.LobInvoice.CustomerDetails.builder();
        if(Objects.nonNull(operatorDetails) && Objects.nonNull(operatorDetails.getUserMobilityInfo())){
            customerDetailsBuilder.address(operatorDetails.getUserMobilityInfo().getAddress())
                    .alternateNumber(Long.parseLong(operatorDetails.getUserMobilityInfo().getAlternateContactNumber()))
                    .customerAccountNo(operatorDetails.getUserMobilityInfo().getCustomerID())
                    .customerClassification(operatorDetails.getUserMobilityInfo().getCustomerClassification())
                    .customerType(operatorDetails.getUserMobilityInfo().getCustomerType())
                    .gstn(operatorDetails.getUserMobilityInfo().getGstNumber())
                    .emailId(operatorDetails.getUserMobilityInfo().getEmailID())
                    .pinCode(operatorDetails.getUserMobilityInfo().getPincode())
                    .panNumber(operatorDetails.getUserMobilityInfo().getPanNumber())
                    .name(operatorDetails.getUserMobilityInfo().getFirstName() + " " + operatorDetails.getUserMobilityInfo().getLastName())
                    .stateCode(operatorDetails.getUserMobilityInfo().getGstStateCode())
                    .stateName(operatorDetails.getUserMobilityInfo().getCircle());
        }
        return customerDetailsBuilder.build();
    }

    private List<InformInvoiceMessage.LobInvoice.CustomerRechargeRate> generateCustomerRechargeRate (TaxableResponse taxableResponse, InvoiceDetails invoiceDetails) {
        final List<InformInvoiceMessage.LobInvoice.CustomerRechargeRate> customerRechargeRatesList = new ArrayList<>();
        customerRechargeRatesList.add(InformInvoiceMessage.LobInvoice.CustomerRechargeRate.builder()
                .rate(taxableResponse.getTaxableAmount())
                .hsnCodeNo(invoiceDetails.getSACCode())
                .category("Prepaid")
                .unit(1)
                .build());
        return customerRechargeRatesList;
    }
    private InformInvoiceMessage.LobInvoice.TaxDetails generateTaxDetails (TaxableResponse taxableResponse) {
        final List<InformInvoiceMessage.LobInvoice.TaxDetails.SubRow> taxDetailsList = new ArrayList<>();
        for(TaxDetailsDTO dto : taxableResponse.getTaxDetails()){
            taxDetailsList.add(InformInvoiceMessage.LobInvoice.TaxDetails.SubRow.builder()
                    .amount(String.valueOf(dto.getAmount()))
                    .taxType(dto.getTaxType().getType())
                    .rate(String.valueOf(dto.getRate()))
                    .build());
        }
        return InformInvoiceMessage.LobInvoice.TaxDetails.builder()
                .taxAmount(String.valueOf(taxableResponse.getTaxAmount()))
                .taxableValue(String.valueOf(taxableResponse.getTaxableAmount()))
                .subRow(taxDetailsList)
                .build();
    }

}
