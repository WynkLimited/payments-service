package in.wynk.payment.mapper;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.dto.PlanDetails;
import in.wynk.payment.dto.PointDetails;
import in.wynk.payment.dto.WebPurchaseDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.service.IPricingManager;
import in.wynk.session.context.SessionContextHolder;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.security.core.context.SecurityContextHolder;

import static in.wynk.common.constant.BaseConstants.CLIENT;

public class DefaultTransactionInitRequestMapper implements IObjectMapper {

    public static AbstractTransactionInitRequest from(IPurchaseDetails purchaseDetails) {
        final IEntityCacheService<PaymentMethod, String> paymentMethodCaching = BeanLocatorFactory.getBean(new ParameterizedTypeReference<IEntityCacheService<PaymentMethod, String>>() {
        });
        final PaymentCode paymentCode = paymentMethodCaching.get(purchaseDetails.getPaymentDetails().getPaymentId()).getPaymentCode();
        final ClientDetailsCachingService clientCachingService = BeanLocatorFactory.getBean(ClientDetailsCachingService.class);
        final Client client = WebPurchaseDetails.class.isAssignableFrom(purchaseDetails.getClass()) ? clientCachingService.getClientByAlias(SessionContextHolder.<SessionDTO>getBody().get(CLIENT)) : clientCachingService.getClientById(SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString());
        final AbstractTransactionInitRequest transactionInitRequest;
        if (PlanDetails.class.isAssignableFrom(purchaseDetails.getProductDetails().getClass())) {
            transactionInitRequest = planInit(client, paymentCode, purchaseDetails.getUserDetails(), purchaseDetails.getAppDetails() ,purchaseDetails.getPaymentDetails(), (PlanDetails) purchaseDetails.getProductDetails());
        } else if (PointDetails.class.isAssignableFrom(purchaseDetails.getProductDetails().getClass())) {
            transactionInitRequest = pointInit(client, paymentCode, purchaseDetails.getUserDetails(), purchaseDetails.getPaymentDetails(), (PointDetails) purchaseDetails.getProductDetails());
        } else {
            throw new WynkRuntimeException("Method is not implemented!");
        }
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(transactionInitRequest);
        return transactionInitRequest;
    }

    public static AbstractTransactionInitRequest from(RefundTransactionRequestWrapper wrapper) {
        final Transaction originalTransaction = wrapper.getOriginalTransaction();
        if (originalTransaction.getType() == PaymentEvent.POINT_PURCHASE) {
            return PointTransactionInitRequest.builder().uid(originalTransaction.getUid()).msisdn(originalTransaction.getMsisdn()).amount(originalTransaction.getAmount()).itemId(originalTransaction.getItemId()).clientAlias(originalTransaction.getClientAlias()).paymentCode(originalTransaction.getPaymentChannel()).event(PaymentEvent.REFUND).build();
        } else {
            return PlanTransactionInitRequest.builder().uid(originalTransaction.getUid()).msisdn(originalTransaction.getMsisdn()).amount(originalTransaction.getAmount()).planId(originalTransaction.getPlanId()).clientAlias(originalTransaction.getClientAlias()).paymentCode(originalTransaction.getPaymentChannel()).event(PaymentEvent.REFUND).build();
        }
    }

    public static AbstractTransactionInitRequest from(IapVerificationRequestWrapper wrapper) {
        final IapVerificationRequest request = wrapper.getVerificationRequest();
        final LatestReceiptResponse receiptResponse = wrapper.getReceiptResponse();
        final ClientDetails clientDetails = (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(wrapper.getClientId());
        AbstractTransactionInitRequest initRequest = PlanTransactionInitRequest.builder().planId(receiptResponse.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentCode(request.getPaymentCode()).event(PaymentEvent.SUBSCRIBE).clientAlias(clientDetails.getAlias()).couponId(receiptResponse.getCouponCode()).autoRenewOpted(receiptResponse.isAutoRenewal()).trialOpted(receiptResponse.isFreeTrial()).build();
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    public static AbstractTransactionInitRequest from(PlanRenewalRequest request) {
        AbstractTransactionInitRequest initRequest = PlanTransactionInitRequest.builder().planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentCode(request.getPaymentCode()).event(PaymentEvent.RENEW).clientAlias(request.getClientAlias()).autoRenewOpted(true).build();
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    public static AbstractTransactionInitRequest from(MigrationTransactionRequest request) {
        AbstractTransactionInitRequest initRequest = PlanTransactionInitRequest.builder().planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentCode(request.getPaymentCode()).event(PaymentEvent.RENEW).clientAlias(request.getClientAlias()).autoRenewOpted(true).status(TransactionStatus.MIGRATED.getValue()).build();
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    private static AbstractTransactionInitRequest planInit(Client clientDetails, PaymentCode paymentCode, IUserDetails payerDetails, IAppDetails appDetails, IPaymentDetails paymentDetails, PlanDetails planDetails) {
        final PaymentEvent paymentEvent = paymentDetails.isAutoRenew() ? PaymentEvent.SUBSCRIBE : PaymentEvent.PURCHASE;
        return PlanTransactionInitRequest.builder().autoRenewOpted(paymentDetails.isAutoRenew()).paymentCode(paymentCode).userDetails(payerDetails).appDetails(appDetails).trialOpted(paymentDetails.isTrialOpted()).couponId(paymentDetails.getCouponId()).planId(planDetails.getPlanId()).clientAlias(clientDetails.getAlias()).event(paymentEvent).msisdn(payerDetails.getMsisdn()).uid(MsisdnUtils.getUidFromMsisdn(payerDetails.getMsisdn())).build();
    }

    private static AbstractTransactionInitRequest pointInit(Client clientDetails, PaymentCode paymentCode, IUserDetails payerDetails,  IPaymentDetails paymentDetails, PointDetails pointDetails) {
        return PointTransactionInitRequest.builder().paymentCode(paymentCode).event(PaymentEvent.POINT_PURCHASE).couponId(paymentDetails.getCouponId()).itemId(pointDetails.getItemId()).clientAlias(clientDetails.getAlias()).msisdn(payerDetails.getMsisdn()).uid(MsisdnUtils.getUidFromMsisdn(payerDetails.getMsisdn())).build();
    }

}
