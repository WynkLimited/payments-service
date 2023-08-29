package in.wynk.payment.service.impl;

import in.wynk.auth.dao.entity.Client;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.payment.core.dao.entity.Invoice;
import in.wynk.payment.core.dao.repository.InvoiceDao;
import in.wynk.payment.service.InvoiceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Slf4j
@Service
public class InvoiceServiceImpl implements InvoiceService {

    @Override
    @CacheEvict(cacheName = "Invoice", cacheKey = "'id:'+ #invoice.getId()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public void upsert(Invoice invoice) {
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), InvoiceDao.class).save(invoice);
    }

    @Override
    @Cacheable(cacheName = "Invoice", cacheKey = "'id:'+ #invoiceId", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public Optional<Invoice> getInvoice(String id) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), InvoiceDao.class).findById(id);
    }

    @Override
    @Cacheable(cacheName = "Invoice", cacheKey = "'transactionId:'+ #transactionId", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public Optional<Invoice> getInvoiceByTransactionId(String transactionId) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), InvoiceDao.class).findByTransactionId(transactionId);
    }

}