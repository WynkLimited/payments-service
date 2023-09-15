package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Invoice;
import in.wynk.payment.core.dao.repository.InvoiceDao;
import in.wynk.payment.core.event.InvoiceEvent;
import in.wynk.payment.service.InvoiceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.Optional;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;
import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Slf4j
@Service
@RequiredArgsConstructor
public class InvoiceServiceImpl implements InvoiceService {

    private final ApplicationEventPublisher eventPublisher;

    @Override
    @CacheEvict(cacheName = "Invoice", cacheKey = "'transactionId:'+ #invoice.getTransactionId()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public Invoice upsert(Invoice invoice) {
        Invoice persistedInvoice = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), InvoiceDao.class).save(invoice);
        final InvoiceEvent.InvoiceEventBuilder builder = InvoiceEvent.builder().invoice(invoice);
        eventPublisher.publishEvent(builder.build());
        publishAnalytics(persistedInvoice);
        evictInvoiceId(invoice.getId());
        return persistedInvoice;
    }

    @CacheEvict(cacheName = "Invoice", cacheKey = "'id:'+ #id", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    private void evictInvoiceId(String id){
    }

    @Override
    @Cacheable(cacheName = "Invoice", cacheKey = "'id:'+ #id", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public Invoice getInvoice(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), InvoiceDao.class).findById(id).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY445));
    }

    @Override
    @Cacheable(cacheName = "Invoice", cacheKey = "'transactionId:'+ #transactionId", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public Invoice getInvoiceByTransactionId(String transactionId) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), InvoiceDao.class).findByTransactionId(transactionId).orElse(null);
    }

    private void publishAnalytics(Invoice invoice) {
        AnalyticService.update(INVOICE_ID, invoice.getId());
        AnalyticService.update(TRANSACTION_ID, invoice.getTransactionId());
        AnalyticService.update("invoiceExternalId", invoice.getInvoiceExternalId());
        AnalyticService.update("amount", invoice.getAmount());
        AnalyticService.update("taxAmount", invoice.getTaxAmount());
        AnalyticService.update("taxableValue", invoice.getTaxableValue());
        AnalyticService.update("cgst", invoice.getCgst());
        AnalyticService.update("sgst", invoice.getSgst());
        AnalyticService.update("igst", invoice.getIgst());
        AnalyticService.update("customerAccountNo", invoice.getCustomerAccountNumber());
        AnalyticService.update("status", invoice.getStatus());
        AnalyticService.update("description", invoice.getDescription());
        AnalyticService.update("createdOn", invoice.getCreatedOn().getTimeInMillis());
        if (Objects.nonNull(invoice.getUpdatedOn())) {
            AnalyticService.update("updatedOn", invoice.getUpdatedOn().getTime().getTime());
        }
        AnalyticService.update("retryCount", invoice.getRetryCount());
    }
}