package in.wynk.payment.service.impl;

import in.wynk.audit.IAuditableListener;
import in.wynk.audit.constant.AuditConstants;
import in.wynk.auth.dao.entity.Client;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.data.entity.MongoBaseEntity;
import in.wynk.payment.constant.CardConstants;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.dao.repository.IRecurringDetailsDao;
import in.wynk.payment.core.dao.repository.receipts.IPurchasingDetailsDao;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.charge.AbstractPaymentDetails;
import in.wynk.payment.dto.request.charge.card.CardPaymentDetails;
import in.wynk.payment.service.IPurchaseDetailsManger;
import lombok.RequiredArgsConstructor;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;

@Service
@RequiredArgsConstructor
public class PurchaseDetailsManager implements IPurchaseDetailsManger {
    @Autowired
    @Qualifier(AuditConstants.MONGO_AUDIT_LISTENER)
    private IAuditableListener auditingListener;
    @Override
    @CacheEvict(cacheName = "PAYMENT_DETAILS_KEY", cacheKey = "#transaction.getIdStr()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public void save(Transaction transaction, IPurchaseDetails details) {
        PurchaseDetails purchaseDetails = PurchaseDetails.builder()
                .id(transaction.getIdStr())
                .appDetails(details.getAppDetails())
                .userDetails(details.getUserDetails())
                .productDetails(details.getProductDetails())
                .geoLocation(details.getGeoLocation())
                .paymentDetails(details.getPaymentDetails())
                .sessionDetails(details.getSessionDetails())
                .pageUrlDetails(((IChargingDetails) details).getPageUrlDetails())
                .callbackUrl(((IChargingDetails) details).getCallbackDetails().getCallbackUrl())
                .build();
        auditingListener.onBeforeSave(purchaseDetails);
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IPurchasingDetailsDao.class).save(purchaseDetails);
    }

    @Override
    @CacheEvict(cacheName = "PAYMENT_DETAILS_KEY", cacheKey = "#transaction.getIdStr()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public void save (Transaction transaction, AbstractPaymentChargingRequest details) {
        AbstractPaymentDetails abstractPaymentDetails = details.getPaymentDetails();
        if(CardPaymentDetails.class.isAssignableFrom(abstractPaymentDetails.getClass())) {
            if (CardConstants.CARD.equals(abstractPaymentDetails.getPaymentMode())) {
                String mode = ((CardPaymentDetails) abstractPaymentDetails).getCardDetails().getCardInfo().getCategory();
                if(mode.equals("CC") || mode.equals("DC")) {
                    abstractPaymentDetails.setPaymentMode(((CardPaymentDetails) abstractPaymentDetails).getCardDetails().getCardInfo().getCategory());
                }
            }
        }
        PurchaseDetails purchaseDetails = PurchaseDetails.builder()
                .id(transaction.getIdStr())
                .appDetails(details.getAppDetails())
                .userDetails(details.getUserDetails())
                .productDetails(details.getProductDetails())
                .paymentDetails(abstractPaymentDetails)
                .geoLocation(details.getGeoLocation())
                .pageUrlDetails(details.getPageUrlDetails())
                .callbackUrl(details.getCallbackDetails().getCallbackUrl())
                .sessionDetails(details.getSessionDetails())
                .build();
        auditingListener.onBeforeSave(purchaseDetails);
        RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IPurchasingDetailsDao.class).save(purchaseDetails);
    }

    @Override
    @Cacheable(cacheName = "PAYMENT_DETAILS_KEY", cacheKey = "#transaction.getIdStr()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public IPurchaseDetails get(Transaction transaction) {
        final Optional<? extends IPurchaseDetails> purchaseDetails = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IPurchasingDetailsDao.class).findById(transaction.getIdStr());
        Optional<Client> abc = ClientContext.getClient();

        if (purchaseDetails.isPresent()) return purchaseDetails.get();
        return getOldData(transaction).orElse(null);
    }

    @Override
    //@Cacheable(cacheName = "PAYMENT_DETAILS_KEYS", cacheKey = "#transaction.getIdStr()", l2CacheTtl = 24 * 60 * 60, cacheManager = L2CACHE_MANAGER)
    public List<String> getByUserId(String userId) {
        final List<PurchaseDetails> purchaseDetailsList = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IPurchasingDetailsDao.class).findByUserId(userId);
        if (CollectionUtils.isEmpty(purchaseDetailsList)) return null;
        return purchaseDetailsList.stream().map(MongoBaseEntity::getId).collect(Collectors.toList());
    }

    @Deprecated
    //TODO One time hack to support data retrieval of current users till 3 days. Kindly remove it in subsequent release. Remove above added if condition also
    public Optional<? extends IPurchaseDetails> getOldData(Transaction transaction) {
        return RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IRecurringDetailsDao.class).findById(RecurringDetails.PurchaseKey.builder().uid(transaction.getUid()).productKey(String.valueOf(transaction.getPlanId())).build());
    }

}