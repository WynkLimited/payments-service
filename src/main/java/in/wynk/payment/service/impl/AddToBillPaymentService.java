package in.wynk.payment.service.impl;

import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.addtobill.AddToBillChargingRequest;
import in.wynk.payment.dto.addtobill.AddToBillEligibilityAndPricingRequest;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.addtobill.*;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.service.*;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service(BeanConstant.ADD_TO_BILL_PAYMENT_SERVICE)
public class AddToBillPaymentService extends AbstractMerchantPaymentStatusService  implements IExternalPaymentEligibilityService, IMerchantPaymentChargingService<AddToBillChargingResponse, AddToBillChargingRequest<?>>, IUserPreferredPaymentService<UserAddToBillDetails, PreferredPaymentDetailsRequest<?>> {
    private final RestTemplate restTemplate;
    private final PaymentCachingService cachingService;

    public AddToBillPaymentService(PaymentCachingService cachingService, IErrorCodesCacheService errorCodesCacheServiceImpl, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, PaymentCachingService cachingService1) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.restTemplate = restTemplate;
        this.cachingService = cachingService1;
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        return null;
    }

    @Override
    public WynkResponseEntity<AddToBillChargingResponse> charge(AddToBillChargingRequest<?> request) {
        return null;
    }


    @Override
    public boolean isEligible(PaymentOptionsPlanEligibilityRequest root) {
        try {
            final PlanDTO plan = cachingService.getPlan(root.getPlanId());
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            //trn? dth? xtream se mangni padegi..dth k liye session se nhi niklta hai?
            //si will be added for dth and dsl
            //session me si dalni padegi aur subscription side pe nikalni padegi

            if (plan.getActivationServiceIds().isEmpty() || StringUtils.isBlank(offer.getServiceGroupId())) {
                return false;
            } else {
                final AddToBillEligibilityAndPricingRequest request = AddToBillEligibilityAndPricingRequest.builder().serviceIds(plan.getActivationServiceIds()).skuGroupId(offer.getServiceGroupId()).si(root.getSi()).channel("DTH").pageIdentifier("DETAILS").build();
                final HttpHeaders headers = generateHeaders();
                HttpEntity<AddToBillEligibilityAndPricingRequest> requestEntity = new HttpEntity<>(request, headers);
                AddToBillEligibilityAndPricingResponse response = restTemplate.exchange("http://10.5.74.34:8099/shop-eligibility/getDetailsEligibilityAndPricing", HttpMethod.POST, requestEntity, AddToBillEligibilityAndPricingResponse.class).getBody();
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
        AddToBillEligibilityAndPricingResponse response = getDetailsEligibilityAndPricing(preferredPaymentDetailsRequest.getProductDetails().getId());
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

    private AddToBillEligibilityAndPricingResponse getDetailsEligibilityAndPricing (String planId) {
        try {
            final PlanDTO plan = cachingService.getPlan(planId);
            final OfferDTO offer = cachingService.getOffer(plan.getLinkedOfferId());
            //changes required in PreferredPaymentDetailsRequest and CombinedPaymentDetailsRequest for values in si from userDetails from session and request
            final AddToBillEligibilityAndPricingRequest request = AddToBillEligibilityAndPricingRequest.builder().serviceIds(plan.getActivationServiceIds()).skuGroupId(offer.getServiceGroupId()).si(null).channel("DTH").pageIdentifier("DETAILS").build();
            final HttpHeaders headers = generateHeaders();
            HttpEntity<AddToBillEligibilityAndPricingRequest> requestEntity = new HttpEntity<>(request, headers);
            AddToBillEligibilityAndPricingResponse response = restTemplate.exchange("http://10.5.74.34:8099/shop-eligibility/getDetailsEligibilityAndPricing", HttpMethod.POST, requestEntity, AddToBillEligibilityAndPricingResponse.class).getBody();
            return response;
        } catch (Exception ex) {
            log.error("Error in AddToBill Eligibility check: {}", ex.getMessage(), ex);
            return null;
        }
    }
}
