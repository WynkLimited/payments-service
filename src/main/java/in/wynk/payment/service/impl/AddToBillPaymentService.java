package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.addtobill.*;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.DefaultChargingRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.UserBillingDetail;
import in.wynk.payment.dto.response.addtobill.*;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.service.*;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;

import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.ADDTOBILL_CHARGING_STATUS_VERIFICATION;
import static in.wynk.payment.dto.addtobill.AddToBillConstants.*;
import static in.wynk.logging.constants.LoggingConstants.REQUEST_ID;
import static in.wynk.cache.constant.BeanConstant.L2CACHE_MANAGER;

@Slf4j
@Service(BeanConstant.ADD_TO_BILL_PAYMENT_SERVICE)
public class AddToBillPaymentService extends AbstractMerchantPaymentStatusService implements IExternalPaymentEligibilityService, IMerchantPaymentChargingService<AddToBillChargingResponse, DefaultChargingRequest<?>>, IUserPreferredPaymentService<UserAddToBillDetails, PreferredPaymentDetailsRequest<?>>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>, ICancellingRecurringService {
    @Value("${payment.merchant.addtobill.api.base.url}")
    private String addToBillBaseUrl;
    @Value("${payment.merchant.addtobill.auth.token}")
    private String addToBillAuthToken;
    private final RestTemplate restTemplate;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final IMerchantTransactionService merchantTransactionService;

    public AddToBillPaymentService(PaymentCachingService cachingService, IErrorCodesCacheService errorCodesCacheServiceImpl, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, PaymentCachingService cachingService1, ApplicationEventPublisher eventPublisher, IMerchantTransactionService merchantTransactionService) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.restTemplate = restTemplate;
        this.cachingService = cachingService1;
        this.eventPublisher = eventPublisher;
        this.merchantTransactionService = merchantTransactionService;
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(ADDTOBILL_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at addToBill end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.ATB02);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(ADDTOBILL_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at APBPaytm end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
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
        final String serviceId = plan.getActivationServiceIds().stream().findFirst().get();
        try {
            List<ServiceOrderItem> serviceOrderItems = new LinkedList<>();
            final ServiceOrderItem serviceOrderItem = ServiceOrderItem.builder().provisionSi(userBillingDetail.getBillingSiDetail().getBillingSi()).serviceId(serviceId).paymentDetails(PaymentDetails.builder().paymentAmount(userAddToBillDetails.getAmount()).build()).serviceOrderMeta(null).build();
            serviceOrderItems.add(serviceOrderItem);
            final AddToBillCheckOutRequest checkOutRequest = AddToBillCheckOutRequest.builder()
                    .channel(DTH)
                    .loggedInSi(userBillingDetail.getSi())
                    .orderPaymentDetails(OrderPaymentDetails.builder().addToBill(true).orderPaymentAmount(userAddToBillDetails.getAmount()).paymentTransactionId(userBillingDetail.getBillingSiDetail().getBillingSi()).optedPaymentMode(OptedPaymentMode.builder().modeId(modeId).modeType(BILL).build()).build())
                    .serviceOrderItems(serviceOrderItems)
                    .orderMeta(null).build();
            final HttpHeaders headers = generateHeaders();
            HttpEntity<AddToBillCheckOutRequest> requestEntity = new HttpEntity<>(checkOutRequest, headers);
            final AddToBillCheckOutResponse response = restTemplate.exchange(addToBillBaseUrl + ADDTOBILL_CHECKOUT_API, HttpMethod.POST, requestEntity, AddToBillCheckOutResponse.class).getBody();
            if (response.isSuccess()) {
                transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
                log.info("ATB charging orderId: {}", response.getBody().getOrderId());
                builder.data(AddToBillChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).build());
                final MerchantTransactionEvent merchantTransactionEvent = MerchantTransactionEvent.builder(transaction.getIdStr()).externalTransactionId(response.getBody().getOrderId()).request(requestEntity).response(response).build();
                eventPublisher.publishEvent(merchantTransactionEvent);
            }

        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            final PaymentErrorType errorType = ATB01;
            builder.error(TechnicalErrorDetails.builder().code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build()).status(errorType.getHttpResponseStatusCode()).success(false);
            log.error(errorType.getMarker(), e.getMessage(), e);
        }
        return builder.build();
    }


    @Override
    public Boolean isEligible(PaymentOptionsPlanEligibilityRequest root) {
        return checkEligibility(root.getPlanId(), root.getSi());
    }

    @Cacheable(cacheName = "AddToBillEligibilityCheck", cacheKey = "'addToBill-eligibility:' + #planId + ':' + #si", l2CacheTtl = 60 * 30, cacheManager = L2CACHE_MANAGER)
    private Boolean checkEligibility(String planId, String si) {
        try {
            final PlanDTO plan = cachingService.getPlan(planId);
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            if (Objects.isNull(plan.getActivationServiceIds()) || StringUtils.isBlank(offer.getServiceGroupId())) {
                log.error("plan serviceIds or offer serviceGroup is not present");
            } else {
                final AddToBillEligibilityAndPricingRequest request = AddToBillEligibilityAndPricingRequest.builder().serviceIds(plan.getActivationServiceIds()).skuGroupId(offer.getServiceGroupId()).si(si).channel("DTH").pageIdentifier("DETAILS").build();
                final HttpHeaders headers = generateHeaders();
                HttpEntity<AddToBillEligibilityAndPricingRequest> requestEntity = new HttpEntity<>(request, headers);
                final AddToBillEligibilityAndPricingResponse response = restTemplate.exchange(addToBillBaseUrl + ADDTOBILL_ELIGIBILITY_API, HttpMethod.POST, requestEntity, AddToBillEligibilityAndPricingResponse.class).getBody();
                if (response.isSuccess() && Objects.nonNull(response.getBody().getServiceList())) {
                    for (EligibleServices eligibleServices : response.getBody().getServiceList()) {
                        if (!eligibleServices.getEligibilityDetails().isIsEligible() || !eligibleServices.getPaymentOptions().contains(ADDTOBILL) || !plan.getActivationServiceIds().contains(eligibleServices.getServiceId())) {
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

    @Override
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

    private HttpHeaders generateHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, addToBillAuthToken);
        headers.add(UNIQUE_TRACKING, MDC.get(REQUEST_ID));
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }
    @Cacheable(cacheName = "AddToBilLinkedSisAndPricing", cacheKey = "'addToBill-linkedSisAndPricing:' + #planId + ':' + #si", l2CacheTtl = 60 * 30, cacheManager = L2CACHE_MANAGER)
    private UserAddToBillDetails getLinkedSisAndPricingDetails(String planId, String si) {
        try {
            final PlanDTO plan = cachingService.getPlan(planId);
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            final AddToBillEligibilityAndPricingRequest request = AddToBillEligibilityAndPricingRequest.builder().serviceIds(plan.getActivationServiceIds()).skuGroupId(offer.getServiceGroupId()).si(si).channel(DTH).pageIdentifier(DETAILS).build();
            final HttpHeaders headers = generateHeaders();
            HttpEntity<AddToBillEligibilityAndPricingRequest> requestEntity = new HttpEntity<>(request, headers);
            AddToBillEligibilityAndPricingResponse response = restTemplate.exchange(addToBillBaseUrl + ADDTOBILL_ELIGIBILITY_API, HttpMethod.POST, requestEntity, AddToBillEligibilityAndPricingResponse.class).getBody();
            for (EligibleServices eligibleServices : response.getBody().getServiceList()) {
                if (eligibleServices.getPaymentOptions().contains(ADDTOBILL) && plan.getActivationServiceIds().contains(eligibleServices.getServiceId())) {
                    return UserAddToBillDetails.builder().linkedSis(eligibleServices.getLinkedSis()).amount(eligibleServices.getPricingDetails().getDiscountedPrice()).build();
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
        final MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(transaction.getIdStr());
        if (Objects.isNull(merchantTransaction)) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException("No merchant transaction found for Subscription");
        }
        try {
            final AddToBillStatusResponse response = getOrderList(si);
            if (Objects.nonNull(response) && response.isSuccess() && !response.getBody().getOrdersList().isEmpty()) {
                for (AddToBillOrder order : response.getBody().getOrdersList()) {
                    if (plan.getActivationServiceIds().contains(order.getServiceId()) && order.getOrderStatus().equalsIgnoreCase(COMPLETED) && order.getEndDate().after(new Date()) && order.getServiceStatus().equalsIgnoreCase(ACTIVE)) {
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
        IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
        final String si = purchaseDetails.getUserDetails().getSi();
        final PlanDTO plan = cachingService.getPlan(paymentRenewalChargingRequest.getPlanId());
        try {
            final AddToBillStatusResponse response = getOrderList(si);
            if (Objects.nonNull(response) && response.isSuccess() && !response.getBody().getOrdersList().isEmpty()) {
                for (AddToBillOrder order : response.getBody().getOrdersList()) {
                    if (plan.getActivationServiceIds().contains(order.getServiceId()) && order.getOrderStatus().equalsIgnoreCase(COMPLETED) && order.getEndDate().after(new Date()) && order.getServiceStatus().equalsIgnoreCase(ACTIVE)) {
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
    public void cancelRecurring(String transactionId) {
        boolean isUnsubscribed = false;
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        Transaction transaction = TransactionContext.get();
        IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
        final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
        try {
            final String si = purchaseDetails.getUserDetails().getSi();
            for (String serviceId : plan.getActivationServiceIds()) {
                final AddToBillStatusResponse resp = getOrderList(si);
                if (Objects.nonNull(resp) && resp.isSuccess() && !resp.getBody().getOrdersList().isEmpty()) {
                    for (AddToBillOrder order : resp.getBody().getOrdersList()) {
                        if (plan.getActivationServiceIds().contains(order.getServiceId()) && order.getOrderStatus().equalsIgnoreCase(COMPLETED) && order.getServiceStatus().equalsIgnoreCase(ACTIVE) && (order.getEndDate().after(new Date()) || order.getEndDate().equals(new Date()))) {
                            final HttpHeaders headers = generateHeaders();
                            final AddToBillUnsubscribeRequest unsubscribeRequest = AddToBillUnsubscribeRequest.builder().msisdn(purchaseDetails.getUserDetails().getMsisdn()).productCode(serviceId).provisionSi(purchaseDetails.getUserDetails().getSi()).source(DIGITAL_STORE).build();
                            final HttpEntity<AddToBillUnsubscribeRequest> unSubscribeRequestEntity = new HttpEntity<>(unsubscribeRequest, headers);
                            final AddToBillUnsubscribeResponse response = restTemplate.exchange(addToBillBaseUrl + ADDTOBILL_UNSUBSCRIBE_API, HttpMethod.POST, unSubscribeRequestEntity, AddToBillUnsubscribeResponse.class).getBody();
                            if (serviceId.equalsIgnoreCase(response.getProductCode()) && response.isMarkedForCancel()) {
                                AnalyticService.update(response);
                                isUnsubscribed = true;
                                log.info("unsubscribe order details si: {},service :{}, endDate {} :", order.getSi(), order.getServiceId(), order.getEndDate());
                                break;
                            }
                        }
                    }
                }

            }

        } catch (Exception e) {
            log.error("Unsubscribe failed: {}", e.getMessage(), e);
        } finally {
            if (isUnsubscribed) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
            log.info("ATB unsubscribe transaction Status {}", finalTransactionStatus.getValue());
        }

    }

    private AddToBillStatusResponse getOrderList(String si) {
        try {
            final HttpHeaders headers = new HttpHeaders();
            headers.add(UNIQUE_TRACKING, MDC.get(REQUEST_ID));
            final String url = addToBillBaseUrl + ADDTOBILL_ORDER_STATUS_API;
            final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam(CHECK_ELIGIBILITY, false)
                    .queryParam(SI, si)
                    .queryParam(CHANNEL, DTH);
            HttpEntity<?> entity = new HttpEntity<>(null, headers);
            AddToBillStatusResponse response = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, entity, AddToBillStatusResponse.class).getBody();
            return response;
        } catch (Exception e) {
            log.error("Failed to get orderList from AddToBill: {} ", e.getMessage(), e);
            return null;
        }
    }
}
