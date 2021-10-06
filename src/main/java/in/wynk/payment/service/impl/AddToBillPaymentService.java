package in.wynk.payment.service.impl;

import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.addtobill.*;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.addtobill.*;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.service.*;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentErrorType.ADDTOBILL01;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.ADDTOBILL_CHARGING_STATUS_VERIFICATION;

@Slf4j
@Service(BeanConstant.ADD_TO_BILL_PAYMENT_SERVICE)
public class AddToBillPaymentService extends AbstractMerchantPaymentStatusService  implements IExternalPaymentEligibilityService, IMerchantPaymentChargingService<AddToBillChargingResponse, AddToBillChargingRequest<?>>, IUserPreferredPaymentService<UserAddToBillDetails, PreferredPaymentDetailsRequest<?>>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>, ICancellingRecurringService {
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
            throw new WynkRuntimeException(PaymentErrorType.ADDTOBILL02);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(ADDTOBILL_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at APBPaytm end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.ADDTOBILL03);
        }
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().status(HttpStatus.OK).data(ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
    }

    @Override
    public WynkResponseEntity<AddToBillChargingResponse> charge(AddToBillChargingRequest<?> request) {
        final WynkResponseEntity.WynkResponseEntityBuilder<AddToBillChargingResponse> builder = WynkResponseEntity.builder();
        final Transaction transaction = TransactionContext.get();
        final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
        final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
        try {
            List<ServiceOrderItem> serviceOrderItems = new LinkedList<>();
            ServiceOrderItem serviceOrderItem = ServiceOrderItem.builder().provisionSi(request.getLinkedSi()).serviceId(request.getServiceId()).paymentDetails(PaymentDetails.builder().paymentAmount(transaction.getAmount()).build()).serviceOrderMeta(null).build();
            serviceOrderItems.add(serviceOrderItem);
            AddToBillCheckOutRequest checkOutRequest = AddToBillCheckOutRequest.builder()
                    .channel("DTH")
                    .loggedInSi(request.getUserDetails().getSi())
                    .orderPaymentDetails(OrderPaymentDetails.builder().addToBill(true).orderPaymentAmount(transaction.getAmount()).paymentTransactionId(request.getLinkedSi()).optedPaymentMode(OptedPaymentMode.builder().modeId("POSTPAID").modeType("BILL").build()).build())
                    .serviceOrderItems(serviceOrderItems)
                    .orderMeta(null).build();
            final HttpHeaders headers = generateHeaders();
            HttpEntity<AddToBillCheckOutRequest> requestEntity = new HttpEntity<>(checkOutRequest, headers);
            AddToBillCheckOutResponse response = restTemplate.exchange("https://kongqa.airtel.com/orderhive/s2s/auth/api/order/proceedToCheckout", HttpMethod.POST, requestEntity, AddToBillCheckOutResponse.class).getBody();
            if (response.isSuccess()) {
                transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
                builder.data(AddToBillChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).build());
                MerchantTransactionEvent merchantTransactionEvent = MerchantTransactionEvent.builder(transaction.getIdStr()).externalTransactionId(response.getBody().getOrderId()).request(requestEntity).response(response).build();
                eventPublisher.publishEvent(merchantTransactionEvent);
            }
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            final PaymentErrorType errorType = ADDTOBILL01;
            builder.error(TechnicalErrorDetails.builder().code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build()).status(errorType.getHttpResponseStatusCode()).success(false);
            log.error(errorType.getMarker(), e.getMessage(), e);
        }
        return builder.build();
    }



    @Override
    public boolean isEligible(PaymentOptionsPlanEligibilityRequest root) {
        try {
            final PlanDTO plan = cachingService.getPlan(root.getPlanId());
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            if (plan.getActivationServiceIds().isEmpty() || StringUtils.isBlank(offer.getServiceGroupId())) {
                return false;
            } else {
                final AddToBillEligibilityAndPricingRequest request = AddToBillEligibilityAndPricingRequest.builder().serviceIds(plan.getActivationServiceIds()).skuGroupId(offer.getServiceGroupId()).si(root.getSi()).channel("DTH").pageIdentifier("DETAILS").build();
                final HttpHeaders headers = generateHeaders();
                HttpEntity<AddToBillEligibilityAndPricingRequest> requestEntity = new HttpEntity<>(request, headers);
                AddToBillEligibilityAndPricingResponse response = restTemplate.exchange("https://kongqa.airtel.com/shop-eligibility/getDetailsEligibilityAndPricing", HttpMethod.POST, requestEntity, AddToBillEligibilityAndPricingResponse.class).getBody();
                //list of services---make sure all are supporting addtobill...else return false.
                //
                if (response.isSuccess() && !response.getBody().getServiceList().isEmpty()) {
                    Optional<EligibleServices> eligibilityResponseBody = response.getBody().getServiceList().stream().filter(eligibleServices -> !eligibleServices.getPaymentOptions().contains("ADDTOBIL")).findAny();
                    if (eligibilityResponseBody.isPresent()) {
                        return false;
                    }
                    return true;
                }
                return false;

            }
        } catch (Exception e) {
            log.error("Error in AddToBill Eligibility check: {}", e.getMessage(), e);
            return false;
        }
    }

    @Override
    public WynkResponseEntity<UserAddToBillDetails> getUserPreferredPayments(PreferredPaymentDetailsRequest<?> preferredPaymentDetailsRequest) {
        WynkResponseEntity.WynkResponseEntityBuilder<UserAddToBillDetails> builder = WynkResponseEntity.builder();
        AddToBillEligibilityAndPricingResponse response = getDetailsEligibilityAndPricing(preferredPaymentDetailsRequest.getProductDetails().getId(), preferredPaymentDetailsRequest.getUserDetails().getSi());
        if(Objects.nonNull(response))
        {
           final List<List<LinkedSis>> linkedSisList = response.getBody().getServiceList().stream().map(EligibleServices::getLinkedSis).collect(Collectors.toList());
           return  builder.data(UserAddToBillDetails.builder().linkedSis(linkedSisList).build()).build();
        }
        return WynkResponseEntity.<UserAddToBillDetails>builder().error(TechnicalErrorDetails.builder().code("ADDTOBILL01").description("saved details not found").build()).data(null).success(false).build();
    }

    private HttpHeaders generateHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.AUTHORIZATION, "Basic b3JkZXJoaXZlLW1pdHJhOmNvbnN1bWVyQG1pdHJh");
        headers.add("uniqueTracking", "afgiuwei");
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
        return headers;
    }

    private AddToBillEligibilityAndPricingResponse getDetailsEligibilityAndPricing (String planId, String si) {
        try {
            final PlanDTO plan = cachingService.getPlan(planId);
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            //changes required in PreferredPaymentDetailsRequest and CombinedPaymentDetailsRequest for values in si from userDetails from session and request
            final AddToBillEligibilityAndPricingRequest request = AddToBillEligibilityAndPricingRequest.builder().serviceIds(plan.getActivationServiceIds()).skuGroupId(offer.getServiceGroupId()).si(si).channel("DTH").pageIdentifier("DETAILS").build();
            final HttpHeaders headers = generateHeaders();
            HttpEntity<AddToBillEligibilityAndPricingRequest> requestEntity = new HttpEntity<>(request, headers);
            AddToBillEligibilityAndPricingResponse response = restTemplate.exchange("https://kongqa.airtel.com/shop-eligibility/getDetailsEligibilityAndPricing", HttpMethod.POST, requestEntity, AddToBillEligibilityAndPricingResponse.class).getBody();
            return response;
        } catch (Exception ex) {
            log.error("Error in AddToBill Eligibility check: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private void fetchAndUpdateTransactionFromSource(Transaction transaction) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        final MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(transaction.getIdStr());
        if (Objects.isNull(merchantTransaction)) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException("No merchant transaction found for Subscription");
        }
            try {
                final HttpHeaders headers = generateHeaders();
                final String url = "https://kongqa.airtel.com/emporio/getUserOrders";
                final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                        .queryParam("checkEligibility", false)
                        .queryParam("si", TransactionContext.getPurchaseDetails().orElse(null).getUserDetails().getSi())
                        .queryParam("channel", "DTH");
                HttpEntity<?> entity = new HttpEntity<>(headers);
                HttpEntity<AddToBillStatusResponse> response = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, entity, AddToBillStatusResponse.class);
                if (response.getBody().isSuccess() && !response.getBody().getBody().getOrdersList().isEmpty()) {
                    for (AddToBillOrder order : response.getBody().getBody().getOrdersList()) {
                        //need to check what can be possible value of getOrderStatus;
                        if (order.getOrderId().equals(merchantTransaction.getExternalTransactionId()) && order.getOrderStatus().equalsIgnoreCase("COMPLETED")) {
                            finalTransactionStatus = TransactionStatus.SUCCESS;
                            transaction.setStatus(finalTransactionStatus.getValue());
                            return;
                        }
                    }
                    finalTransactionStatus = TransactionStatus.FAILURE;
                }
            }
            catch (Exception e) {
                log.error("Failed to get Status from AddToBill: {} ", e.getMessage(), e);
                finalTransactionStatus = TransactionStatus.FAILURE;
            }

        transaction.setStatus(finalTransactionStatus.getValue());
    }

    @Override
    public WynkResponseEntity<Void> doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        Transaction transaction = TransactionContext.get();
        IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
        final String si = purchaseDetails.getUserDetails().getSi();
        final PlanDTO plan = cachingService.getPlan(paymentRenewalChargingRequest.getPlanId());
        final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
        final String serviceGroupId = offer.getServiceGroupId();

        try {
            final HttpHeaders headers = generateHeaders();
            final String url = "https://kongqa.airtel.com/emporio/getUserOrders";
            final UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url)
                    .queryParam("checkEligibility", false)
                    .queryParam("si", si)
                    .queryParam("channel", "DTH");
            HttpEntity<?> entity = new HttpEntity<>(headers);
            HttpEntity<AddToBillStatusResponse> response = restTemplate.exchange(builder.toUriString(), HttpMethod.GET, entity, AddToBillStatusResponse.class);
            if (response.getBody().isSuccess() && !response.getBody().getBody().getOrdersList().isEmpty()) {
                for (AddToBillOrder order : response.getBody().getBody().getOrdersList()) {
                    if (order.getServiceId().equalsIgnoreCase(serviceGroupId) && order.getOrderStatus().equalsIgnoreCase("COMPLETED") && order.getEndDate().after(new Date())) {
                        finalTransactionStatus = TransactionStatus.SUCCESS;
                        transaction.setStatus(finalTransactionStatus.getValue());
                    }
                }
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } catch (Exception e) {
            log.error("Failed to get renewal Status from AddToBill: {} ", e.getMessage(), e);
            finalTransactionStatus = TransactionStatus.FAILURE;
        }
        return WynkResponseEntity.<Void>builder().build();
    }

    @Override
    public void cancelRecurring(String transactionId) {
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        Transaction transaction = TransactionContext.get();
        IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().get();
        final PlanDTO plan = cachingService.getPlan(transaction.getPlanId());
        try {
            for (String serviceId : plan.getActivationServiceIds()) {
                final HttpHeaders headers = generateHeaders();

                AddToBillUnsubscribeRequest unsubscribeRequest = AddToBillUnsubscribeRequest.builder().msisdn(purchaseDetails.getUserDetails().getMsisdn()).productCode(serviceId).provisionSi(purchaseDetails.getUserDetails().getSi()).source("DIGITAL_STORE").build();
                HttpEntity<AddToBillUnsubscribeRequest> unSubscribeRequestEntity = new HttpEntity<>(unsubscribeRequest, headers);
                String unsubscribeResponse = restTemplate.exchange("https://kongqa.airtel.com/enigma/v2/unsubscription", HttpMethod.POST, unSubscribeRequestEntity, String.class).getBody();
                // if(unsubscribeResponse is success)
                finalTransactionStatus = TransactionStatus.SUCCESS;
            }

        } catch (Exception e) {
            log.error("Unsubscribe failed: {}", e.getMessage(), e);
            finalTransactionStatus = TransactionStatus.FAILURE;
        }

    }
}
