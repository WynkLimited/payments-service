package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.auth.dao.entity.Client;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.data.enums.State;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.dao.entity.RecurringDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.IRecurringDetailsDao;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.UserBillingDetail;
import in.wynk.payment.dto.addtobill.ATBOrderStatus;
import in.wynk.payment.dto.aps.common.HealthStatus;
import in.wynk.payment.dto.common.AbstractPaymentInstrumentsProxy;
import in.wynk.payment.dto.common.BillingOptionInfo;
import in.wynk.payment.dto.common.BillingSavedInfo;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.DefaultChargingRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.addtobill.AddToBillChargingResponse;
import in.wynk.payment.dto.response.addtobill.UserAddToBillDetails;
import in.wynk.payment.eligibility.request.PaymentOptionsItemEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.gateway.IPaymentInstrumentsProxy;
import in.wynk.payment.service.*;
import in.wynk.stream.producer.IKafkaEventPublisher;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.message.CancelMandateEvent;
import in.wynk.vas.client.dto.atb.*;
import in.wynk.vas.client.service.CatalogueVasClientService;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;
import static in.wynk.payment.core.constant.PaymentErrorType.ATB01;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY201;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.ADDTOBILL_API_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.ADDTOBILL_CHARGING_STATUS_VERIFICATION;
import static in.wynk.payment.dto.addtobill.ATBOrderStatus.COMPLETED;
import static in.wynk.payment.dto.addtobill.ATBOrderStatus.DEFERRED_COMPLETED;
import static in.wynk.payment.dto.addtobill.AddToBillConstants.*;

@Slf4j
@Service(BeanConstant.ADD_TO_BILL_PAYMENT_SERVICE)
public class AddToBillPaymentService extends AbstractMerchantPaymentStatusService implements IExternalPaymentEligibilityService, IPaymentInstrumentsProxy<PaymentOptionsPlanEligibilityRequest>, IMerchantPaymentChargingService<AddToBillChargingResponse, DefaultChargingRequest<?>>, IUserPreferredPaymentService<UserAddToBillDetails, PreferredPaymentDetailsRequest<?>>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>, ICancellingRecurringService {

    private final PaymentMethodCachingService payCache;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final CatalogueVasClientService catalogueVasClientService;

    private final IKafkaEventPublisher<String, CancelMandateEvent> kafkaPublisherService;

    public AddToBillPaymentService(PaymentCachingService cachingService, IErrorCodesCacheService errorCodesCacheServiceImpl, PaymentMethodCachingService payCache, ApplicationEventPublisher eventPublisher, CatalogueVasClientService catalogueVasClientService, IKafkaEventPublisher<String, CancelMandateEvent> kafkaPublisherService) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.payCache = payCache;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.catalogueVasClientService = catalogueVasClientService;
        this.kafkaPublisherService = kafkaPublisherService;
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.warn(ADDTOBILL_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at addToBill end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.ATB02);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(ADDTOBILL_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at addToBill end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.ATB03);
        }
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().status(HttpStatus.OK).data(ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
    }

    @Override
    public WynkResponseEntity<AddToBillChargingResponse> charge(DefaultChargingRequest<?> request) {
        final WynkResponseEntity.WynkResponseEntityBuilder<AddToBillChargingResponse> builder = WynkResponseEntity.builder();
        final Transaction transaction = TransactionContext.get();
        final UserBillingDetail userBillingDetail = (UserBillingDetail) TransactionContext.getPurchaseDetails().get().getUserDetails();
        final String modeId = userBillingDetail.getBillingSiDetail().getLob().equalsIgnoreCase(POSTPAID) ? MOBILITY : TELEMEDIA;
        final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
        final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
        final UserAddToBillDetails userAddToBillDetails = getLinkedSisAndPricingDetails(transaction.getPlanId().toString(), userBillingDetail.getSi(), request.getAppId());
        try {
            Map<String, Object> serviceOrderMeta = new HashMap<>();
            serviceOrderMeta.put(TXN_ID, transaction.getIdStr());
            serviceOrderMeta.put(BaseConstants.IS_BUNDLE, offer.isThanksBundle());
            List<ServiceOrderItem> serviceOrderItems = new LinkedList<>();
            final ServiceOrderItem serviceOrderItem = ServiceOrderItem.builder().provisionSi(userBillingDetail.getSi()).serviceId(plan.getSku().get(ATB)).paymentDetails(PaymentDetails.builder().paymentAmount(userAddToBillDetails.getAmount()).build()).serviceOrderMeta(serviceOrderMeta).build();
            serviceOrderItems.add(serviceOrderItem);
            final AddToBillCheckOutRequest checkOutRequest = AddToBillCheckOutRequest.builder()
                    .channel(DTH)
                    .loggedInSi(userBillingDetail.getSi())
                    .orderPaymentDetails(OrderPaymentDetails.builder().addToBill(true).orderPaymentAmount(userAddToBillDetails.getAmount()).paymentTransactionId(userBillingDetail.getBillingSiDetail().getBillingSi()).optedPaymentMode(OptedPaymentMode.builder().modeId(modeId).modeType(BILL).build()).build())
                    .serviceOrderItems(serviceOrderItems)
                    .orderMeta(null).build();
            final AddToBillCheckOutResponse response = catalogueVasClientService.checkout(checkOutRequest);
            if (response.isSuccess()) {
                transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
                log.info("ATB checkout success: {}, txnId: {} and OrderId: {}", true, transaction.getIdStr(), response.getBody().getOrderId());
                builder.data(AddToBillChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).build());
                final MerchantTransactionEvent merchantTransactionEvent = MerchantTransactionEvent.builder(transaction.getIdStr()).externalTransactionId(response.getBody().getOrderId()).request(checkOutRequest).response(response).build();
                flushEligibilityCache(String.valueOf(plan.getId()), userBillingDetail.getSi());
                eventPublisher.publishEvent(merchantTransactionEvent);
            }

        } catch (Exception e) {
            if (WynkRuntimeException.class.isAssignableFrom(e.getClass())) {
                final WynkRuntimeException wynkException = (WynkRuntimeException) e;
                if (ConnectException.class.isAssignableFrom(wynkException.getCause().getClass())) {
                    transaction.setStatus(TransactionStatus.FAILURE.getValue());
                    final PaymentErrorType errorType = ATB01;
                    builder.error(TechnicalErrorDetails.builder().code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build()).status(errorType.getHttpResponseStatusCode()).data(AddToBillChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).build()).success(false);
                }
            }
            log.error(ADDTOBILL_API_FAILURE, "add to bill charging is failed", e);
        }
        return builder.build();
    }

    @Cacheable(cacheName = "AddToBillEligibilityCheck", cacheKey = "'addToBill-eligibility:' + #planId + ':' + #si", l2CacheTtl = 60 * 30, cacheManager = L2CACHE_MANAGER)
    private CatalogueEligibilityAndPricingResponse getEligibility(String planId, String si, String appId) {
        try {
            final PlanDTO plan = cachingService.getPlan(planId);
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            if (MapUtils.isEmpty(plan.getSku()) || !plan.getSku().containsKey(ATB) || StringUtils.isBlank(offer.getServiceGroupId())) {
                log.warn("plan serviceIds or offer serviceGroup is not present");
                return null;
            } else {
                final CatalogueEligibilityAndPricingRequest request = CatalogueEligibilityAndPricingRequest.builder().serviceIds(Collections.singletonList(plan.getSku().get(ATB))).skuGroupId(offer.getServiceGroupId()).si(si).channel(DTH).pageIdentifier(DETAILS).isBundle(offer.isThanksBundle()).build();
                final CatalogueEligibilityAndPricingResponse response = catalogueVasClientService.getEligibility(request, Boolean.TRUE, appId);
                return response;
            }
        } catch (Exception e) {
            return null;
        }
    }

    @CacheEvict(cacheName = "AddToBillEligibilityCheck", cacheKey = "'addToBill-eligibility:' + #planId + ':' + #si", l2CacheTtl = 60 * 30, cacheManager = L2CACHE_MANAGER)
    private void flushEligibilityCache(String planId, String si) {
        log.info("eligibility cache Cleared for planId {} and si {}", planId, si);
    }

    @Override
    @ClientAware(clientAlias = "#request.clientAlias")
    public WynkResponseEntity<UserAddToBillDetails> getUserPreferredPayments(PreferredPaymentDetailsRequest<?> request) {
        WynkResponseEntity.WynkResponseEntityBuilder<UserAddToBillDetails> builder = WynkResponseEntity.builder();
        if (StringUtils.isNotBlank(request.getSi())) {
            final UserAddToBillDetails userAddToBillDetails = getLinkedSisAndPricingDetails(request.getProductDetails().getId(), request.getSi(), "");
            if (Objects.nonNull(userAddToBillDetails)) {
                return builder.data(userAddToBillDetails).build();
            }
        }
        final PaymentErrorType errorType = PAY201;
        return WynkResponseEntity.<UserAddToBillDetails>builder().error(TechnicalErrorDetails.builder().code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build()).data(null).success(false).build();
    }

    private UserAddToBillDetails getLinkedSisAndPricingDetails(String planId, String si, String appId) {
        try {
            final PlanDTO plan = cachingService.getPlan(planId);
            final CatalogueEligibilityAndPricingResponse response = this.getEligibility(planId, si, appId);
            if (Objects.nonNull(response)) {
                for (EligibleServices eligibleServices : response.getBody().getServiceList()) {
                    if (eligibleServices.getPaymentOptions().contains(ADDTOBILL) && plan.getSku().get(ATB).equalsIgnoreCase(eligibleServices.getServiceId())) {
                        return UserAddToBillDetails.builder().linkedSis(eligibleServices.getLinkedSis()).amount(eligibleServices.getPricingDetails().getDiscountedPrice()).build();
                    }
                }
            }
            return null;
        } catch (Exception ex) {
            return null;
        }
    }

    private void fetchAndUpdateTransactionFromSource(Transaction transaction) {
        IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final UserBillingDetail userBillingDetail = (UserBillingDetail) TransactionContext.getPurchaseDetails().get().getUserDetails();
        final PlanDTO plan = cachingService.getPlan(purchaseDetails.getProductDetails().getId());
        try {
            final OrderStatusResponse response = getOrderList(userBillingDetail.getSi());
            if(Objects.isNull(response)) {
                finalTransactionStatus= TransactionStatus.FAILURE;
            } else if (Objects.nonNull(response) && response.isSuccess() && !response.getBody().getOrdersList().isEmpty()) {
                for (CatalogueOrder order : response.getBody().getOrdersList()) {
                    if (plan.getSku().get(ATB).equalsIgnoreCase(order.getServiceId()) && order.getOrderMeta().containsKey(TXN_ID) && order.getOrderMeta().get(TXN_ID).toString().equals(transaction.getIdStr())) {
                        if (Objects.nonNull(order.getEndDate()) && ((COMPLETED.name().equalsIgnoreCase(order.getOrderStatus()) && order.getEndDate().after(new Date()) && ACTIVE.equalsIgnoreCase(order.getServiceStatus()))
                                || (DEFERRED_COMPLETED.name().equalsIgnoreCase(order.getOrderStatus()) && order.getEndDate().after(new Date())))) {
                            finalTransactionStatus = TransactionStatus.SUCCESS;
                            transaction.setStatus(finalTransactionStatus.getValue());
                            log.info("ATB order status success: {}, for provisionSi: {}, loggedInSi: {} ,service: {} and endDate is: {}", true, order.getSi(), order.getLoggedInSi(), order.getServiceId(), order.getEndDate());
                            return;
                        } else if (ATBOrderStatus.FAILED.name().equalsIgnoreCase(order.getOrderStatus())) {
                            finalTransactionStatus = TransactionStatus.FAILURE;
                            transaction.setStatus(finalTransactionStatus.getValue());
                            log.info("ATB order status success: {}, for provisionSi: {}, loggedInSi: {} and service: {}", false, order.getSi(), order.getLoggedInSi(), order.getServiceId());
                            return;
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to get Status from AddToBill: {} ", e.getMessage(), e);
        }

        transaction.setStatus(finalTransactionStatus.getValue());
    }

    @Override
    public WynkResponseEntity<Void> doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        boolean status = false;
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        Transaction transaction = TransactionContext.get();
        IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().orElseThrow(() -> new WynkRuntimeException("Purchase details is not found"));
        final UserBillingDetail userBillingDetail = (UserBillingDetail) TransactionContext.getPurchaseDetails().get().getUserDetails();
        final PlanDTO plan = cachingService.getPlan(paymentRenewalChargingRequest.getPlanId());
        try {
            final OrderStatusResponse response = getOrderList(userBillingDetail.getSi());
            if (Objects.nonNull(response) && response.isSuccess() && !response.getBody().getOrdersList().isEmpty()) {
                for (CatalogueOrder order : response.getBody().getOrdersList()) {
                    if (plan.getSku().get(ATB).equalsIgnoreCase(order.getServiceId()) && order.getSi().equalsIgnoreCase(userBillingDetail.getSi())) {
                        if((order.getOrderStatus().equalsIgnoreCase(COMPLETED.name()) && order.getEndDate().after(new Date()) && order.getServiceStatus().equalsIgnoreCase(ACTIVE))
                                || (order.getOrderStatus().equalsIgnoreCase(DEFERRED_COMPLETED.name()) && order.getEndDate().after(new Date()))){
                            status = true;
                            log.info("ATB renewal order status success: {}, for provisionSi: {}, loggedInSi: {} ,service: {} and endDate is: {}", true, order.getSi(), order.getLoggedInSi(), order.getServiceId(), order.getEndDate());
                            break;
                        }
                    } else if (plan.getSku().get(ATB).equalsIgnoreCase(order.getServiceId()) && order.getSi().equalsIgnoreCase(userBillingDetail.getSi()) && order.getOrderStatus().equalsIgnoreCase(ATBOrderStatus.FAILED.name())) {
                        status = false;
                        log.info("ATB renewal order status success: {}, for provisionSi: {}, loggedInSi: {} and service: {}", false, order.getSi(), order.getLoggedInSi(), order.getServiceId());
                        break;
                    }
                }
                if (status) {
                    finalTransactionStatus = TransactionStatus.SUCCESS;
                } else {
                    finalTransactionStatus = TransactionStatus.FAILURE;
                }
            } else {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }

        } catch (Exception e) {
            log.error("Failed to get renewal Status from AddToBill: {} ", e.getMessage(), e);
            finalTransactionStatus = TransactionStatus.FAILURE;
        }
        transaction.setStatus(finalTransactionStatus.getValue());
        return WynkResponseEntity.<Void>builder().build();
    }

    @Override
    @TransactionAware(txnId = "#transactionId")
    public void cancelRecurring(String transactionId, PaymentEvent paymentEvent) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        Transaction transaction = TransactionContext.get();
        final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
        try {
            final IRecurringDetailsDao paymentDetailsDao = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), IRecurringDetailsDao.class);
            Optional<? extends IPurchaseDetails> purchaseDetails = paymentDetailsDao.findById(RecurringDetails.PurchaseKey.builder().uid(transaction.getUid()).productKey(String.valueOf(transaction.getPlanId())).build());
            if (purchaseDetails.isPresent()) {
                final UserBillingDetail userDetails = (UserBillingDetail) purchaseDetails.get().getUserDetails();
                final AddToBillUnsubscribeRequest unsubscribeRequest = AddToBillUnsubscribeRequest.builder().msisdn(userDetails.getBillingSiDetail().getBillingSi()).productCode(plan.getSku().get(ATB)).provisionSi(userDetails.getSi()).source(DIGITAL_STORE).build();
                final AddToBillUnsubscribeResponse response = catalogueVasClientService.unsubscribe(unsubscribeRequest);
                AnalyticService.update(response);
                CancelMandateEvent mandateEvent= CancelMandateEvent.builder().planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).paymentEvent(paymentEvent).addToBillUnsubscribeResponse(CancelMandateEvent.AddToBillUnsubscribeResponse.builder()
                        .lob(response.getLob())
                        .chargingCycle(response.getChargingCycle())
                        .chargingPrice(response.getChargingPrice())
                        .isIsMarkedForCancel(response.isIsMarkedForCancel())
                        .optin(response.isOptin())
                        .productPrice(response.getProductPrice())
                        .si(response.getSi())
                        .markedForCancel(response.isMarkedForCancel())
                        .provisionSi(response.getProvisionSi())
                        .subProductCode(response.getSubProductCode())
                        .unsubscriptionReason(response.getUnsubscriptionReason())
                        .waiverEligible(response.isWaiverEligible())
                        .productCode(response.getProductCode()).build())
                        .build();
                kafkaPublisherService.publish(mandateEvent);
                if (plan.getSku().get(ATB).equalsIgnoreCase(response.getProductCode()) && response.isMarkedForCancel()) {
                    finalTransactionStatus = TransactionStatus.SUCCESS;
                    log.info("unsubscribe order details si: {},service :{}, markedForCanceled {} :", response.getSi(), response.getProductCode(), response.isIsMarkedForCancel());
                }
            } else {
                finalTransactionStatus = TransactionStatus.FAILURE;
                log.error("Unsubscribe failed because Purchase Details not found");
            }

        } catch (Exception e) {
            finalTransactionStatus = TransactionStatus.FAILURE;
            log.error("Unsubscribe failed: {}", e.getMessage(), e);
        }
    }

    private OrderStatusResponse getOrderList(String si) {
        try {
            return catalogueVasClientService.ordersStatus(si);
        } catch (Exception e) {
            log.error(ADDTOBILL_API_FAILURE, "recon is failed due to {} ", e);
            return null;
        }
    }

    @Override
    public boolean isEligible(PaymentMethod entity, PaymentOptionsPlanEligibilityRequest request) {
        try {
            final BillPaymentInstrumentsProxy proxy = ((BillPaymentInstrumentsProxy) request.getPaymentInstrumentsProxy(entity.getPaymentCode().getCode()));
            if (Objects.isNull(proxy)) return Boolean.FALSE;
            final PlanDTO plan = cachingService.getPlan(proxy.getPlanId());
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            if (MapUtils.isEmpty(plan.getSku()) || !plan.getSku().containsKey(ATB) || StringUtils.isBlank(offer.getServiceGroupId())) {
                log.warn("plan serviceIds or offer serviceGroup is not present");
            } else {
                final CatalogueEligibilityAndPricingResponse response = proxy.getResponse();
                if (Objects.nonNull(response) && response.isSuccess() && Objects.nonNull(response.getBody().getServiceList())) {
                    for (EligibleServices eligibleServices : response.getBody().getServiceList()) {
                        if (!eligibleServices.getEligibilityDetails().isIsEligible() || !eligibleServices.getPaymentOptions().contains(ADDTOBILL) ||
                                !plan.getSku().get(ATB).equalsIgnoreCase(eligibleServices.getServiceId())) {
                            return false;
                        }
                        //WCF-5039: Check if logged in si and linked si is same if postpaid and if any other return that SI.
                        boolean anyMatchWithOriginalSi = eligibleServices.getLinkedSis().stream().anyMatch(linkedSis ->
                                !POSTPAID.equals(linkedSis.getLob()) || linkedSis.getSi().equals(response.getBody().getSi()));
                        if (!anyMatchWithOriginalSi) {
                            log.warn("User is not eligible for atb because user is eligible on linked si {} but not on logged in si {}",
                                    eligibleServices.getLinkedSis(), response.getBody().getSi());
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
    //AddToBill is not supported for item
    @Override
    public boolean isEligible (PaymentMethod entity, PaymentOptionsItemEligibilityRequest request) {
        return false;
    }

    @Override
    public AbstractPaymentInstrumentsProxy<?, ?> load(PaymentOptionsPlanEligibilityRequest request) {
        return new BillPaymentInstrumentsProxy(request.getPlanId(), request.getSi(), request.getAppId());
    }

    @Getter
    private class BillPaymentInstrumentsProxy extends AbstractPaymentInstrumentsProxy<BillingOptionInfo, BillingSavedInfo> {

        private final String planId;
        private final List<BillingOptionInfo> billingOptionCache;
        private final List<BillingSavedInfo> billingSavedInfoCache;
        private final CatalogueEligibilityAndPricingResponse response;

        public BillPaymentInstrumentsProxy(String planId, String si, String appId) {
            super();
            this.planId = planId;
            this.response = getEligibility(planId, si, appId);
            this.billingSavedInfoCache = getSavedDetails(si);
            this.billingOptionCache = getPaymentInstruments(si);
        }

        @Override
        public List<BillingOptionInfo> getPaymentInstruments(String userId) {
            if (Objects.nonNull(billingOptionCache)) return billingOptionCache;
            final PaymentMethod method = payCache.get(BaseConstants.ADDTOBILL);
            return Collections.singletonList(BillingOptionInfo.builder()
                    .id(BaseConstants.ADDTOBILL)
                    .recommended(Boolean.TRUE)
                    .order(method.getHierarchy())
                    .enabled(method.getState().equals(State.ACTIVE))
                    .title(method.getDisplayName())
                    .build());
        }

        @Override
        public List<BillingSavedInfo> getSavedDetails(String userId) {
            if (Objects.nonNull(billingSavedInfoCache)) return billingSavedInfoCache;
            final PlanDTO plan = cachingService.getPlan(planId);
            final PaymentMethod method = payCache.get(BaseConstants.ADDTOBILL);
            final List<BillingSavedInfo> savedInfoList = new ArrayList<>();
            final BillingSavedInfo.BillingSavedInfoBuilder<?, ?> builder = BillingSavedInfo.builder();
            if (Objects.nonNull(response)) {
                for (EligibleServices eligibleServices : response.getBody().getServiceList()) {
                    if (eligibleServices.getPaymentOptions().contains(ADDTOBILL) && plan.getSku().get(ATB).equalsIgnoreCase(eligibleServices.getServiceId())) {
                        List<LinkedSis> linkedSi =
                                eligibleServices.getLinkedSis().stream().filter(linkedSis -> !POSTPAID.equals(linkedSis.getLob()) || linkedSis.getSi().equals(response.getBody().getSi()))
                                        .collect(Collectors.toList());
                        builder.linkedSis(linkedSi).enable(response.isSuccess());
                        break;
                    }
                }
                savedInfoList.add(builder
                        .autoPayEnabled(method.isAutoRenewSupported())
                        .code(method.getPaymentCode().getCode())
                        .id(method.getId())
                        .type(method.getGroup())
                        .group(method.getGroup())
                        .recommended(Boolean.TRUE)
                        .valid(response.isSuccess())
                        .health(HealthStatus.UP.name())
                        .iconUrl(method.getIconUrl())
                        .order(method.getHierarchy())
                        .title(method.getDisplayName())
                        .expressCheckout(Boolean.TRUE)
                        .build());
            }
            return savedInfoList;
        }

    }

}