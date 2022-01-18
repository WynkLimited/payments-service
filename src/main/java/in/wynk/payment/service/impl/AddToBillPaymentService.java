package in.wynk.payment.service.impl;

import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.dao.repository.IRecurringDetailsDao;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.TransactionContext;
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
import in.wynk.vas.client.dto.atb.AddToBillEligibilityAndPricingRequest;
import in.wynk.vas.client.service.ATBVasClientService;
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
import static in.wynk.payment.dto.addtobill.AddToBillConstants.*;

@Slf4j
@Service(BeanConstant.ADD_TO_BILL_PAYMENT_SERVICE)
public class AddToBillPaymentService extends AbstractMerchantPaymentStatusService implements IExternalPaymentEligibilityService, IMerchantPaymentChargingService<AddToBillChargingResponse, DefaultChargingRequest<?>>, IUserPreferredPaymentService<UserAddToBillDetails, PreferredPaymentDetailsRequest<?>>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>, ICancellingRecurringService {

    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final IRecurringDetailsDao paymentDetailsDao;
    private final ATBVasClientService atbVasClientService;

    public AddToBillPaymentService(PaymentCachingService cachingService, IErrorCodesCacheService errorCodesCacheServiceImpl, PaymentCachingService cachingService1, ApplicationEventPublisher eventPublisher, IRecurringDetailsDao paymentDetailsDao, ATBVasClientService atbVasClientService) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.cachingService = cachingService1;
        this.eventPublisher = eventPublisher;
        this.paymentDetailsDao = paymentDetailsDao;
        this.atbVasClientService = atbVasClientService;
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
            List<ServiceOrderItem> serviceOrderItems = new LinkedList<>();
            final ServiceOrderItem serviceOrderItem = ServiceOrderItem.builder().provisionSi(userBillingDetail.getBillingSiDetail().getBillingSi()).serviceId(plan.getSku().get(ATB)).paymentDetails(PaymentDetails.builder().paymentAmount(userAddToBillDetails.getAmount()).build()).serviceOrderMeta(null).build();
            serviceOrderItems.add(serviceOrderItem);
            final AddToBillCheckOutRequest checkOutRequest = AddToBillCheckOutRequest.builder()
                    .channel(DTH)
                    .loggedInSi(userBillingDetail.getSi())
                    .orderPaymentDetails(OrderPaymentDetails.builder().addToBill(true).orderPaymentAmount(userAddToBillDetails.getAmount()).paymentTransactionId(userBillingDetail.getBillingSiDetail().getBillingSi()).optedPaymentMode(OptedPaymentMode.builder().modeId(modeId).modeType(BILL).build()).build())
                    .serviceOrderItems(serviceOrderItems)
                    .orderMeta(null).build();
            final AddToBillCheckOutResponse response = atbVasClientService.checkout(checkOutRequest);
            if (response.isSuccess()) {
                transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
                log.info("ATB charging orderId: {}", response.getBody().getOrderId());
                builder.data(AddToBillChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).build());
                final MerchantTransactionEvent merchantTransactionEvent = MerchantTransactionEvent.builder(transaction.getIdStr()).externalTransactionId(response.getBody().getOrderId()).request(checkOutRequest).response(response).build();
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
                final AddToBillEligibilityAndPricingResponse response = this.getEligibility(planId, si);
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
    private AddToBillEligibilityAndPricingResponse getEligibility(String planId, String si) {
        try {
            final PlanDTO plan = cachingService.getPlan(planId);
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            if (MapUtils.isEmpty(plan.getSku()) || !plan.getSku().containsKey(ATB) || StringUtils.isBlank(offer.getServiceGroupId())) {
                log.error("plan serviceIds or offer serviceGroup is not present");
                return null;
            } else {
                final AddToBillEligibilityAndPricingRequest request = AddToBillEligibilityAndPricingRequest.builder().serviceIds(Collections.singletonList(plan.getSku().get(ATB))).skuGroupId(offer.getServiceGroupId()).si(si).channel(DTH).pageIdentifier(DETAILS).build();
                final AddToBillEligibilityAndPricingResponse response = atbVasClientService.getEligibility(request);
                return response;
            }
        } catch (Exception e) {
            log.error("Error in AddToBill Eligibility check: {}", e.getMessage(), e);
            return null;
        }
    }

    @Override
    @ClientAware(clientAlias = "#request.clientAlias")
    public WynkResponseEntity<UserAddToBillDetails> getUserPreferredPayments(PreferredPaymentDetailsRequest<?> preferredPaymentDetailsRequest) {
        WynkResponseEntity.WynkResponseEntityBuilder<UserAddToBillDetails> builder = WynkResponseEntity.builder();
        if (StringUtils.isNotBlank(preferredPaymentDetailsRequest.getSi())) {
            final UserAddToBillDetails userAddToBillDetails = getLinkedSisAndPricingDetails(preferredPaymentDetailsRequest.getProductDetails().getId(), preferredPaymentDetailsRequest.getSi());
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
            final AddToBillEligibilityAndPricingResponse response = this.getEligibility(planId, si);
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
        final String si = TransactionContext.getPurchaseDetails().map(IPurchaseDetails::getUserDetails).map(IUserDetails::getSi).orElse(null);
        final PlanDTO plan = cachingService.getPlan(purchaseDetails.getProductDetails().getId());
        try {
            final AddToBillStatusResponse response = getOrderList(si);
            if (Objects.nonNull(response) && response.isSuccess() && !response.getBody().getOrdersList().isEmpty()) {
                for (AddToBillOrder order : response.getBody().getOrdersList()) {
                    if (plan.getSku().get(ATB).equalsIgnoreCase(order.getServiceId()) && order.getOrderStatus().equalsIgnoreCase(COMPLETED) && order.getEndDate().after(new Date()) && order.getServiceStatus().equalsIgnoreCase(ACTIVE)) {
                        finalTransactionStatus = TransactionStatus.SUCCESS;
                        transaction.setStatus(finalTransactionStatus.getValue());
                        log.info("Order subscription details si: {},service :{}, endDate {} :", order.getSi(), order.getServiceId(), order.getEndDate());
                        return;
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
        final String si = purchaseDetails.getUserDetails().getSi();
        final PlanDTO plan = cachingService.getPlan(paymentRenewalChargingRequest.getPlanId());
        try {
            final AddToBillStatusResponse response = getOrderList(si);
            if (Objects.nonNull(response) && response.isSuccess() && !response.getBody().getOrdersList().isEmpty()) {
                for (AddToBillOrder order : response.getBody().getOrdersList()) {
                    if (plan.getSku().get(ATB).equalsIgnoreCase(order.getServiceId()) && order.getOrderStatus().equalsIgnoreCase(COMPLETED) && order.getEndDate().after(new Date()) && order.getServiceStatus().equalsIgnoreCase(ACTIVE)) {
                        status = true;
                        log.info("Order subscription details si: {},service :{}, endDate {} :", order.getSi(), order.getServiceId(), order.getEndDate());
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
            Optional<? extends IPurchaseDetails> purchaseDetails = paymentDetailsDao.findById(RecurringDetails.PurchaseKey.builder().uid(transaction.getUid()).productKey(String.valueOf(transaction.getPlanId())).build());
            if (purchaseDetails.isPresent()) {
                final UserBillingDetail userDetails = (UserBillingDetail) purchaseDetails.get().getUserDetails();
                    final AddToBillUnsubscribeRequest unsubscribeRequest = AddToBillUnsubscribeRequest.builder().msisdn(userDetails.getBillingSiDetail().getBillingSi()).productCode(plan.getSku().get(ATB)).provisionSi(userDetails.getSi()).source(DIGITAL_STORE).build();
                    final AddToBillUnsubscribeResponse response = atbVasClientService.unsubscribe(unsubscribeRequest);
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
            return atbVasClientService.ordersStatus(si);
        } catch (Exception e) {
            log.error("Failed to get orderList from AddToBill: {} ", e.getMessage(), e);
            return null;
        }
    }
}