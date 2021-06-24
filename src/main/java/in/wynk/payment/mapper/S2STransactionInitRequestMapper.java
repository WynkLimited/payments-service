package in.wynk.payment.mapper;

import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.LatestReceiptResponse;
import in.wynk.payment.service.IPricingManager;
import org.springframework.security.core.context.SecurityContextHolder;

public class S2STransactionInitRequestMapper implements IObjectMapper {

    public static AbstractTransactionInitRequest from(AbstractChargingRequest<?> request) {
        AbstractTransactionInitRequest initRequest =  AbstractChargingRequest.AbstractPointChargingDetails.class.isAssignableFrom(request.getChargingDetails().getClass()) ? pointInit(request.getPaymentCode(), (AbstractChargingRequest.PointS2SChargingDetails) request.getChargingDetails()) : planInit(request.getPaymentCode(), (AbstractChargingRequest.PlanS2SChargingDetails) request.getChargingDetails());
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    public static AbstractTransactionInitRequest from(WalletTopUpRequest<?> request) {
        AbstractTransactionInitRequest initRequest =  AbstractChargingRequest.AbstractPointChargingDetails.class.isAssignableFrom(request.getChargingDetails().getClass()) ? pointInit(request.getPaymentCode(), (AbstractChargingRequest.PointS2SChargingDetails) request.getChargingDetails()) : planInit(request.getPaymentCode(), (AbstractChargingRequest.PlanS2SChargingDetails) request.getChargingDetails());
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

    public static AbstractTransactionInitRequest from(IapVerificationRequestWrapper wrapper) {
        final IapVerificationRequest request = wrapper.getVerificationRequest();
        final LatestReceiptResponse receiptResponse = wrapper.getReceiptResponse();
        final ClientDetails clientDetails = (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(wrapper.getClientId());
        AbstractTransactionInitRequest initRequest = PlanTransactionInitRequest.builder().planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentCode(request.getPaymentCode()).event(PaymentEvent.SUBSCRIBE).clientAlias(clientDetails.getAlias()).autoRenewOpted(receiptResponse.isAutoRenewal()).trialOpted(receiptResponse.isFreeTrial()).build();
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    private static AbstractTransactionInitRequest planInit(PaymentCode code, AbstractChargingRequest.PlanS2SChargingDetails planDetails) {
        final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        final PaymentEvent paymentEvent = planDetails.isAutoRenew() ? PaymentEvent.SUBSCRIBE: PaymentEvent.PURCHASE;
        final ClientDetails clientDetails = (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(clientId);
        return PlanTransactionInitRequest.builder().autoRenewOpted(planDetails.isAutoRenew()).paymentCode(code).trialOpted(planDetails.isTrialOpted()).couponId(planDetails.getCouponId()).planId(planDetails.getPlanId()).clientAlias(clientDetails.getAlias()).event(paymentEvent).msisdn(planDetails.getMsisdn()).uid(MsisdnUtils.getUidFromMsisdn(planDetails.getMsisdn())).build();
    }

    private static AbstractTransactionInitRequest pointInit(PaymentCode code, AbstractChargingRequest.PointS2SChargingDetails pointDetails) {
        final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        final ClientDetails clientDetails = (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(clientId);
        return PointTransactionInitRequest.builder().paymentCode(code).event(PaymentEvent.POINT_PURCHASE).couponId(pointDetails.getCouponId()).itemId(pointDetails.getItemId()).clientAlias(clientDetails.getAlias()).amount(pointDetails.getAmount()).msisdn(pointDetails.getMsisdn()).uid(MsisdnUtils.getUidFromMsisdn(pointDetails.getMsisdn())).build();
    }

}
