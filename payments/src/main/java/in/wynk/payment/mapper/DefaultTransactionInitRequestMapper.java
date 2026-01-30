package in.wynk.payment.mapper;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.IGeoLocation;
import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.gpbs.GooglePlayStatusCodes;
import in.wynk.payment.dto.gpbs.request.GooglePlayVerificationRequest;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayLatestReceiptResponse;
import in.wynk.payment.dto.gpbs.response.receipt.GooglePlayProductReceiptResponse;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.service.IPricingManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.MerchantServiceUtil;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import org.apache.commons.lang.StringUtils;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.CLIENT;

public class DefaultTransactionInitRequestMapper implements IObjectMapper {
    private static IEntityCacheService<PaymentMethod, String> paymentMethodCaching = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IEntityCacheService<PaymentMethod, String>>() {
    });

    public static AbstractTransactionInitRequest from(IPurchaseDetails purchaseDetails) {
        final IEntityCacheService<PaymentMethod, String> paymentMethodCaching = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IEntityCacheService<PaymentMethod, String>>() {
        });
        final PaymentGateway paymentGateway = paymentMethodCaching.get(purchaseDetails.getPaymentDetails().getPaymentId()).getPaymentCode();
        final ClientDetailsCachingService clientCachingService = BeanLocatorFactory.getBean(ClientDetailsCachingService.class);
        final Client client = WebPurchaseDetails.class.isAssignableFrom(purchaseDetails.getClass()) ? clientCachingService.getClientByAlias(SessionContextHolder.<SessionDTO>getBody().get(CLIENT)) : clientCachingService.getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        final AbstractTransactionInitRequest transactionInitRequest;
        if (PlanDetails.class.isAssignableFrom(purchaseDetails.getProductDetails().getClass())) {
            transactionInitRequest = planInit(client, paymentGateway, purchaseDetails.getUserDetails(), purchaseDetails.getAppDetails(), purchaseDetails.getPaymentDetails(), (PlanDetails) purchaseDetails.getProductDetails(), purchaseDetails.getGeoLocation());
        } else if (PointDetails.class.isAssignableFrom(purchaseDetails.getProductDetails().getClass())) {
            transactionInitRequest = pointInit(client, paymentGateway, purchaseDetails.getUserDetails(), purchaseDetails.getAppDetails(), purchaseDetails.getPaymentDetails(), (PointDetails) purchaseDetails.getProductDetails());
        } else {
            throw new WynkRuntimeException("Method is not implemented!");
        }
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(transactionInitRequest);
        return transactionInitRequest;
    }

    public static AbstractTransactionInitRequest from(RefundTransactionRequestWrapper wrapper) {
        final Transaction originalTransaction = wrapper.getOriginalTransaction();
        if (originalTransaction.getType() == PaymentEvent.POINT_PURCHASE) {
            return PointTransactionInitRequest.builder().txnId(originalTransaction.getIdStr()).uid(originalTransaction.getUid()).msisdn(originalTransaction.getMsisdn()).amount(originalTransaction.getAmount()).itemId(originalTransaction.getItemId()).clientAlias(originalTransaction.getClientAlias()).paymentGateway(originalTransaction.getPaymentChannel()).event(PaymentEvent.REFUND).build();
        } else {
            return PlanTransactionInitRequest.builder().txnId(originalTransaction.getIdStr()).uid(originalTransaction.getUid()).msisdn(originalTransaction.getMsisdn()).amount(originalTransaction.getAmount()).planId(originalTransaction.getPlanId()).clientAlias(originalTransaction.getClientAlias()).paymentGateway(originalTransaction.getPaymentChannel()).event(PaymentEvent.REFUND).build();
        }
    }

    public static AbstractTransactionInitRequest from(IapVerificationRequestWrapper wrapper) {
        final IapVerificationRequest request = wrapper.getVerificationRequest();
        final LatestReceiptResponse receiptResponse = wrapper.getReceiptResponse();
        final PlanDTO selectedPlan = BeanLocatorFactory.getBean(PaymentCachingService.class).getPlan(receiptResponse.getPlanId());
        final ClientDetails clientDetails = (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(wrapper.getClientId());
        final AbstractTransactionInitRequest initRequest = PlanTransactionInitRequest.builder().planId(receiptResponse.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentGateway(request.getPaymentGateway()).event((request.getPaymentGateway().getId().equals(PaymentConstants.AMAZON_IAP) || request.isOriginalSid()) ? PaymentEvent.PURCHASE : PaymentEvent.RENEW).clientAlias(clientDetails.getAlias()).couponId(receiptResponse.getCouponCode()).autoRenewOpted(receiptResponse.isAutoRenewal()).trialOpted(receiptResponse.isFreeTrial()).userDetails(UserDetails.builder().msisdn(request.getMsisdn()).build()).appDetails(AppDetails.builder().os(request.getOs()).deviceId(request.getDeviceId()).service(selectedPlan.getService()).buildNo(request.getBuildNo()).build()).build();
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    public static AbstractTransactionInitRequest fromV2 (IapVerificationRequestWrapper wrapper, PaymentCachingService cachingService) {
        final IapVerificationRequestV2 request = wrapper.getVerificationRequestV2();
        GooglePlayVerificationRequest gRequest = (GooglePlayVerificationRequest) request;
        final LatestReceiptResponse receiptResponse = wrapper.getReceiptResponse();
        GooglePlayLatestReceiptResponse googleResponse = (GooglePlayLatestReceiptResponse) receiptResponse;
        final ClientDetails clientDetails = (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(wrapper.getClientId());
        PlanDTO planDTO = cachingService.getPlanFromSku(gRequest.getProductDetails().getSkuId());
        AbstractTransactionInitRequest initRequest = null;
        if (planDTO != null) {
            final PlanDTO selectedPlan = BeanLocatorFactory.getBean(PaymentCachingService.class).getPlan(planDTO.getId());
            if (!selectedPlan.getService().equalsIgnoreCase(gRequest.getAppDetails().getService())) {
                throw new WynkRuntimeException(GooglePlayStatusCodes.GOOGLE_31020.getErrorTitle());
            }
            initRequest = PlanTransactionInitRequest.builder().planId(receiptResponse.getPlanId())
                    .uid(gRequest.getUserDetails().getUid()).msisdn(gRequest.getUserDetails().getMsisdn()).paymentGateway(request.getPaymentCode())
                    .event(MerchantServiceUtil.getGooglePlayEvent(gRequest, googleResponse, BaseConstants.PLAN))
                    .autoRenewOpted(MerchantServiceUtil.getAutoRenewalOpted(gRequest, receiptResponse))
                    .clientAlias(clientDetails.getAlias()).couponId(receiptResponse.getCouponCode()).trialOpted(receiptResponse.isFreeTrial())
                    .userDetails(UserDetails.builder().msisdn(gRequest.getUserDetails().getMsisdn()).build())
                    .appDetails(AppDetails.builder().os(gRequest.getAppDetails().getOs()).deviceId(gRequest.getAppDetails().getDeviceId()).service(selectedPlan.getService())
                            .buildNo(gRequest.getAppDetails().getBuildNo()).build()).build();
        } else {
            String itemId = request.getProductDetails().getItemId();
            if (Objects.isNull(itemId)) {
                GooglePlayProductReceiptResponse googlePlayProductResponse = (GooglePlayProductReceiptResponse) googleResponse.getGooglePlayResponse();
                itemId = StringUtils.isNotEmpty(googlePlayProductResponse.getDeveloperPayload()) ? googlePlayProductResponse.getDeveloperPayload() : itemId;
            }
            initRequest =
                    PointTransactionInitRequest.builder().itemId(itemId).uid(gRequest.getUserDetails().getUid()).msisdn(gRequest.getUserDetails().getMsisdn()).paymentGateway(request.getPaymentCode())
                            .event(MerchantServiceUtil.getGooglePlayEvent(gRequest, googleResponse, BaseConstants.POINT))
                            .clientAlias(clientDetails.getAlias()).amount(request.getProductDetails().getPrice())
                            .build();
        }
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    public static AbstractTransactionInitRequest from(PlanRenewalRequest request) {
        final AbstractTransactionInitRequest initRequest = PlanTransactionInitRequest.builder().planId(request.getPlanId()).txnId(request.getTxnId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentGateway(request.getPaymentGateway()).event(PaymentEvent.RENEW).clientAlias(request.getClientAlias()).autoRenewOpted(true).build();
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    public static AbstractTransactionInitRequest from(MigrationTransactionRequest request) {
        final AbstractTransactionInitRequest initRequest = PlanTransactionInitRequest.builder().planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentGateway(request.getPaymentCode()).event(PaymentEvent.RENEW).clientAlias(request.getClientAlias()).autoRenewOpted(true).status(TransactionStatus.MIGRATED.getValue()).build();
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    private static AbstractTransactionInitRequest planInit(Client clientDetails, PaymentGateway paymentGateway, IUserDetails payerDetails, IAppDetails appDetails, IPaymentDetails paymentDetails, PlanDetails planDetails, IGeoLocation geoLocation) {
        return PlanTransactionInitRequest.builder().mandate(paymentDetails.isMandate()).autoRenewOpted(paymentDetails.isAutoRenew()).paymentGateway(paymentGateway).userDetails(payerDetails).appDetails(appDetails).trialOpted(paymentDetails.isTrialOpted()).couponId(paymentDetails.getCouponId()).planId(planDetails.getPlanId()).clientAlias(clientDetails.getAlias()).event(paymentDetails.isMandate() ? PaymentEvent.MANDATE : PaymentEvent.PURCHASE).msisdn(payerDetails.getMsisdn()).uid(IdentityUtils.getUidFromUserName(payerDetails.getMsisdn(), appDetails.getService())).geoDetails(geoLocation).build();
    }

    private static AbstractTransactionInitRequest pointInit(Client clientDetails, PaymentGateway paymentGateway, IUserDetails payerDetails, IAppDetails appDetails, IPaymentDetails paymentDetails, PointDetails pointDetails) {
        return PointTransactionInitRequest.builder().paymentGateway(paymentGateway).event(PaymentEvent.POINT_PURCHASE).couponId(paymentDetails.getCouponId()).itemId(pointDetails.getItemId()).clientAlias(clientDetails.getAlias()).msisdn(payerDetails.getMsisdn()).uid(IdentityUtils.getUidFromUserName(payerDetails.getMsisdn(), appDetails.getService())).build();
    }

    public static AbstractTransactionInitRequest from (AbstractPaymentChargingRequest request) {
        final PaymentGateway paymentGateway = paymentMethodCaching.get(request.getPaymentDetails().getPaymentId()).getPaymentCode();
        final Client client = request.getClientDetails();
        final AbstractTransactionInitRequest transactionInitRequest;
        if (PlanDetails.class.isAssignableFrom(request.getProductDetails().getClass())) {
            transactionInitRequest = planInit(client, paymentGateway, request.getUserDetails(), request.getAppDetails(), request.getPaymentDetails(), (PlanDetails) request.getProductDetails(), request.getGeoLocation());
        } else if (PointDetails.class.isAssignableFrom(request.getProductDetails().getClass())) {
            transactionInitRequest = pointInit(client, paymentGateway, request.getUserDetails(), request.getAppDetails(), request.getPaymentDetails(), (PointDetails) request.getProductDetails());
        } else {
            throw new WynkRuntimeException("Method is not implemented!");
        }
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(transactionInitRequest);
        return transactionInitRequest;
    }
}