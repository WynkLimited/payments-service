package in.wynk.payment.mapper;

import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.IObjectMapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.service.IPricingManager;
import org.springframework.security.core.context.SecurityContextHolder;

public class S2STransactionInitRequestMapper implements IObjectMapper {

    public static AbstractTransactionInitRequest from(AbstractChargingRequest<?> request) {
        AbstractTransactionInitRequest initRequest =  AbstractChargingRequest.AbstractPointChargingDetails.class.isAssignableFrom(request.getChargingDetails().getClass()) ? pointInit(request) : planInit(request);
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    public static AbstractTransactionInitRequest from(PlanRenewalRequest request) {
        AbstractTransactionInitRequest initRequest = PlanTransactionInitRequest.builder().planId(request.getPlanId()).uid(request.getUid()).msisdn(request.getMsisdn()).paymentCode(request.getPaymentCode()).event(PaymentEvent.RENEW).clientAlias(request.getClientAlias()).autoRenewOpted(true).build();
        BeanLocatorFactory.getBean(IPricingManager.class).computePriceAndApplyDiscount(initRequest);
        return initRequest;
    }

    private static AbstractTransactionInitRequest planInit(AbstractChargingRequest<?> request) {
        final AbstractChargingRequest.PlanS2SChargingDetails planDetails = (AbstractChargingRequest.PlanS2SChargingDetails) request.getChargingDetails();
        final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        final PaymentEvent paymentEvent = planDetails.isAutoRenew() ? PaymentEvent.SUBSCRIBE: PaymentEvent.PURCHASE;
        final ClientDetails clientDetails = (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(clientId);
        return PlanTransactionInitRequest.builder().autoRenewOpted(planDetails.isAutoRenew()).paymentCode(request.getPaymentCode()).trialOpted(planDetails.isTrialOpted()).couponId(planDetails.getCouponId()).planId(planDetails.getPlanId()).clientAlias(clientDetails.getAlias()).event(paymentEvent).msisdn(planDetails.getMsisdn()).uid(MsisdnUtils.getUidFromMsisdn(planDetails.getMsisdn())).build();
    }

    private static AbstractTransactionInitRequest pointInit(AbstractChargingRequest<?> request) {
        final AbstractChargingRequest.PointS2SChargingDetails pointDetails = (AbstractChargingRequest.PointS2SChargingDetails) request.getChargingDetails();
        final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        final ClientDetails clientDetails = (ClientDetails) BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(clientId);
        return PointTransactionInitRequest.builder().paymentCode(request.getPaymentCode()).event(PaymentEvent.POINT_PURCHASE).couponId(pointDetails.getCouponId()).itemId(pointDetails.getItemId()).clientAlias(clientDetails.getAlias()).amount(pointDetails.getAmount()).msisdn(pointDetails.getMsisdn()).uid(MsisdnUtils.getUidFromMsisdn(pointDetails.getMsisdn())).build();
    }

}
