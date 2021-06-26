package in.wynk.payment.mapper;

import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.service.IPricingManager;
import org.springframework.security.core.context.SecurityContextHolder;

public class DefaultTransactionInitRequestMapper implements IObjectMapper {

    public static AbstractTransactionInitRequest from(AbstractChargingRequest<?> request) {
        if (PlanDetails.class.isAssignableFrom(request.getPurchaseDetails().getProductDetails().getClass())) {
            return planInit(request.getPurchaseDetails().getPayerDetails(), request.getPurchaseDetails().getPaymentDetails(), (PlanDetails) request.getPurchaseDetails().getProductDetails());
        } else if (PointDetails.class.isAssignableFrom(request.getPurchaseDetails().getProductDetails().getClass())) {
            return pointInit(request.getPurchaseDetails().getPayerDetails(), request.getPurchaseDetails().getPaymentDetails(), (PointDetails) request.getPurchaseDetails().getProductDetails());
        }
        throw new WynkRuntimeException("Method is not implemented!");
    }

    public static AbstractTransactionInitRequest from(WalletTopUpRequest<?> request) {
        if (PlanDetails.class.isAssignableFrom(request.getPurchaseDetails().getProductDetails().getClass())) {
            return planInit(request.getPurchaseDetails().getPayerDetails(), request.getPurchaseDetails().getPaymentDetails(), (PlanDetails) request.getPurchaseDetails().getProductDetails());
        } else if (PointDetails.class.isAssignableFrom(request.getPurchaseDetails().getProductDetails().getClass())) {
            return pointInit(request.getPurchaseDetails().getPayerDetails(), request.getPurchaseDetails().getPaymentDetails(), (PointDetails) request.getPurchaseDetails().getProductDetails());
        }
        throw new WynkRuntimeException("Method is not implemented!");
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
        AbstractTransactionInitRequest initRequest = PlanTransactionInitRequest.builder().planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentCode(request.getPaymentCode()).event(PaymentEvent.SUBSCRIBE).clientAlias(clientDetails.getAlias()).autoRenewOpted(receiptResponse.isAutoRenewal()).trialOpted(receiptResponse.isFreeTrial()).build();
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

    private static AbstractTransactionInitRequest planInit(IPayerDetails payerDetails, PaymentDetails paymentDetails, PlanDetails planDetails) {
        final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        final PaymentEvent paymentEvent = planDetails.isAutoRenew() ? PaymentEvent.SUBSCRIBE : PaymentEvent.PURCHASE;
        final ClientDetails clientDetails = (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(clientId);
        return PlanTransactionInitRequest.builder().autoRenewOpted(planDetails.isAutoRenew()).paymentCode(paymentDetails.getPaymentCode()).trialOpted(planDetails.isTrialOpted()).couponId(paymentDetails.getCouponId()).planId(planDetails.getPlanId()).clientAlias(clientDetails.getAlias()).event(paymentEvent).msisdn(payerDetails.getMsisdn()).uid(MsisdnUtils.getUidFromMsisdn(payerDetails.getMsisdn())).build();
    }

    private static AbstractTransactionInitRequest pointInit(IPayerDetails payerDetails, PaymentDetails paymentDetails, PointDetails pointDetails) {
        final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        final ClientDetails clientDetails = (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(clientId);
        return PointTransactionInitRequest.builder().paymentCode(paymentDetails.getPaymentCode()).event(PaymentEvent.POINT_PURCHASE).couponId(paymentDetails.getCouponId()).itemId(pointDetails.getItemId()).clientAlias(clientDetails.getAlias()).msisdn(payerDetails.getMsisdn()).uid(MsisdnUtils.getUidFromMsisdn(payerDetails.getMsisdn())).build();
    }

}
