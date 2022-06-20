package in.wynk.payment.service.impl;

import in.wynk.auth.dao.entity.Client;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.dao.repository.IRecurringDetailsDao;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.addtobill.ATBOrderStatus;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.DefaultChargingRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.UserBillingDetail;
import in.wynk.payment.dto.response.addtobill.AddToBillChargingResponse;
import in.wynk.payment.dto.response.addtobill.UserAddToBillDetails;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.service.*;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.vas.client.dto.atb.*;
import in.wynk.vas.client.dto.atb.CatalogueEligibilityAndPricingRequest;
import in.wynk.vas.client.service.CatalogueVasClientService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;

import java.util.*;

import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.ADDTOBILL_CHARGING_STATUS_VERIFICATION;
import static in.wynk.payment.dto.addtobill.ATBOrderStatus.COMPLETED;
import static in.wynk.payment.dto.addtobill.AddToBillConstants.*;

@Slf4j
@Service(BeanConstant.ADD_TO_BILL_PAYMENT_SERVICE)
public class AddToBillPaymentService extends AbstractMerchantPaymentStatusService implements IExternalPaymentEligibilityService, IMerchantPaymentChargingService<AddToBillChargingResponse, DefaultChargingRequest<?>>, IUserPreferredPaymentService<UserAddToBillDetails, PreferredPaymentDetailsRequest<?>>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>, ICancellingRecurringService {

    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final CatalogueVasClientService catalogueVasClientService;

    public AddToBillPaymentService(PaymentCachingService cachingService, IErrorCodesCacheService errorCodesCacheServiceImpl, ApplicationEventPublisher eventPublisher, IRecurringDetailsDao paymentDetailsDao, CatalogueVasClientService catalogueVasClientService) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.catalogueVasClientService = catalogueVasClientService;
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(ADDTOBILL_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at addToBill end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
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
        final UserAddToBillDetails userAddToBillDetails = getLinkedSisAndPricingDetails(transaction.getPlanId().toString(), userBillingDetail.getSi());
        try {
            Map<String, Object> serviceOrderMeta = new HashMap<>();
            serviceOrderMeta.put(TXN_ID, transaction.getIdStr());
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
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            final PaymentErrorType errorType = ATB01;
            builder.error(TechnicalErrorDetails.builder().code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build()).status(errorType.getHttpResponseStatusCode()).data(AddToBillChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).build()).success(false);
            log.error(errorType.getMarker(), e.getMessage(), e);
        }
        return builder.build();
    }


    @Override
    public Boolean isEligible(PaymentOptionsPlanEligibilityRequest root) {
        return checkEligibility(root.getPlanId(), root.getSi());
    }

    private Boolean checkEligibility(String planId, String si) {
        try {
            final PlanDTO plan = cachingService.getPlan(planId);
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            if (MapUtils.isEmpty(plan.getSku()) || !plan.getSku().containsKey(ATB) || StringUtils.isBlank(offer.getServiceGroupId())) {
                log.error("plan serviceIds or offer serviceGroup is not present");
            } else {
                final CatalogueEligibilityAndPricingResponse response = this.getEligibility(planId, si);
                if (Objects.nonNull(response) && response.isSuccess() && Objects.nonNull(response.getBody().getServiceList())) {
                    for (EligibleServices eligibleServices : response.getBody().getServiceList()) {
                        if (!eligibleServices.getEligibilityDetails().isIsEligible() || !eligibleServices.getPaymentOptions().contains(ADDTOBILL) || !plan.getSku().get(ATB).equalsIgnoreCase(eligibleServices.getServiceId())) {
                            return false;
                        }
                    }
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            log.error("Error in AddToBill Eligibility check: {}", e.getMessage(), e);
            return false;
        }
    }


    @Cacheable(cacheName = "AddToBillEligibilityCheck", cacheKey = "'addToBill-eligibility:' + #planId + ':' + #si", l2CacheTtl = 60 * 30, cacheManager = L2CACHE_MANAGER)
    private CatalogueEligibilityAndPricingResponse getEligibility(String planId, String si) {
        try {
            final PlanDTO plan = cachingService.getPlan(planId);
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            if (MapUtils.isEmpty(plan.getSku()) || !plan.getSku().containsKey(ATB) || StringUtils.isBlank(offer.getServiceGroupId())) {
                log.error("plan serviceIds or offer serviceGroup is not present");
                return null;
            } else {
                final CatalogueEligibilityAndPricingRequest request = CatalogueEligibilityAndPricingRequest.builder().serviceIds(Collections.singletonList(plan.getSku().get(ATB))).skuGroupId(offer.getServiceGroupId()).si(si).channel(DTH).pageIdentifier(DETAILS).build();
                final CatalogueEligibilityAndPricingResponse response = catalogueVasClientService.getEligibility(request, Boolean.TRUE);
                return response;
            }
        } catch (Exception e) {
            log.error("Error in AddToBill Eligibility check: {}", e.getMessage(), e);
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
            final UserAddToBillDetails userAddToBillDetails = getLinkedSisAndPricingDetails(request.getProductDetails().getId(), request.getSi());
            if (Objects.nonNull(userAddToBillDetails)) {
                return builder.data(userAddToBillDetails).build();
            }
        }
        final PaymentErrorType errorType = PAY201;
        return WynkResponseEntity.<UserAddToBillDetails>builder().error(TechnicalErrorDetails.builder().code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build()).data(null).success(false).build();
    }

    private UserAddToBillDetails getLinkedSisAndPricingDetails(String planId, String si) {
        try {
            final PlanDTO plan = cachingService.getPlan(planId);
            final CatalogueEligibilityAndPricingResponse response = this.getEligibility(planId, si);
            if (Objects.nonNull(response)) {
                for (EligibleServices eligibleServices : response.getBody().getServiceList()) {
                    if (eligibleServices.getPaymentOptions().contains(ADDTOBILL) && plan.getSku().get(ATB).equalsIgnoreCase(eligibleServices.getServiceId())) {
                        return UserAddToBillDetails.builder().linkedSis(eligibleServices.getLinkedSis()).amount(eligibleServices.getPricingDetails().getDiscountedPrice()).build();
                    }
                }
            }
            return null;
        } catch (Exception ex) {
            log.error("Error in AddToBill Eligibility check: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private void fetchAndUpdateTransactionFromSource(Transaction transaction) {
        IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final UserBillingDetail userBillingDetail = (UserBillingDetail) TransactionContext.getPurchaseDetails().get().getUserDetails();
        final PlanDTO plan = cachingService.getPlan(purchaseDetails.getProductDetails().getId());
        try {
            final AddToBillStatusResponse response = getOrderList(userBillingDetail.getSi());
            if (Objects.nonNull(response) && response.isSuccess() && !response.getBody().getOrdersList().isEmpty()) {
                for (AddToBillOrder order : response.getBody().getOrdersList()) {
                    if (plan.getSku().get(ATB).equalsIgnoreCase(order.getServiceId()) && order.getOrderMeta().containsKey(TXN_ID) && order.getOrderMeta().get(TXN_ID).toString().equals(transaction.getIdStr())) {
                        if (order.getOrderStatus().equalsIgnoreCase(COMPLETED.name()) && order.getEndDate().after(new Date()) && order.getServiceStatus().equalsIgnoreCase(ACTIVE)) {
                            finalTransactionStatus = TransactionStatus.SUCCESS;
                            transaction.setStatus(finalTransactionStatus.getValue());
                            log.info("ATB order status success: {}, for provisionSi: {}, loggedInSi: {} ,service: {} and endDate is: {}", true, order.getSi(), order.getLoggedInSi(), order.getServiceId(), order.getEndDate());
                            return;
                        } else if (order.getOrderStatus().equalsIgnoreCase(ATBOrderStatus.FAILED.name())) {
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
            final AddToBillStatusResponse response = getOrderList(userBillingDetail.getSi());
            if (Objects.nonNull(response) && response.isSuccess() && !response.getBody().getOrdersList().isEmpty()) {
                for (AddToBillOrder order : response.getBody().getOrdersList()) {
                    if (plan.getSku().get(ATB).equalsIgnoreCase(order.getServiceId()) && order.getSi().equalsIgnoreCase(userBillingDetail.getSi()) && order.getOrderStatus().equalsIgnoreCase(COMPLETED.name()) && order.getEndDate().after(new Date()) && order.getServiceStatus().equalsIgnoreCase(ACTIVE)) {
                        status = true;
                        log.info("ATB renewal order status success: {}, for provisionSi: {}, loggedInSi: {} ,service: {} and endDate is: {}", true, order.getSi(), order.getLoggedInSi(), order.getServiceId(), order.getEndDate());
                        break;
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
    public void cancelRecurring(String transactionId) {
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

    private AddToBillStatusResponse getOrderList(String si) {
        try {
            return catalogueVasClientService.ordersStatus(si,Boolean.TRUE);
        } catch (Exception e) {
            log.error("Failed to get orderList from AddToBill: {} ", e.getMessage(), e);
            return null;
        }
    }
}