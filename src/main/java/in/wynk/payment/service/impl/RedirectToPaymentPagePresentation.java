package in.wynk.payment.service.impl;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.common.constant.BaseConstants.BUILD_NO;
import static in.wynk.common.constant.BaseConstants.DEVICE_ID_SHORT;
import static in.wynk.common.constant.BaseConstants.INGRESS_INTENT;
import static in.wynk.common.constant.BaseConstants.PAYMENT_FLOW;
import static in.wynk.common.constant.BaseConstants.QUESTION_MARK;
import static in.wynk.common.constant.BaseConstants.SLASH;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_FLOW_AUTO_RENEW;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_FLOW_MANDATE;
import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_FLOW_TRIAL_OPTED;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.SessionResponse;
import in.wynk.common.dto.SessionResponse.SessionData;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.BestValuePlanResponse;
import in.wynk.payment.dto.PurchaseRequest;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import java.net.URISyntaxException;
import java.util.Objects;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.data.util.Pair;
import org.springframework.security.core.context.SecurityContextHolder;

public class RedirectToPaymentPagePresentation implements
    IPresentation<WynkResponseEntity<SessionResponse.SessionData>, Pair<String, BestValuePlanResponse>> {

    @Override
    public WynkResponseEntity<SessionData> transform(final Pair<String, BestValuePlanResponse> pair) throws URISyntaxException {
        final String id = pair.getFirst();
        final BestValuePlanResponse bestValuePlanResponse = pair.getSecond();
        final String bestValuePlanId = bestValuePlanResponse.getPlanId();
        final PurchaseRequest request = bestValuePlanResponse.getPurchaseRequest();
        final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        final Client client = BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(clientId);
        final String PAYMENT_OPTION_URL = EmbeddedPropertyResolver.resolveEmbeddedValue(PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c",
                                                                                                                                           client.getAlias())
                                                                                                                                  .replace("%p",
                                                                                                                                           "payOption"),
                                                                                        "${payment.payOption.page}");
        ;
        try {
            if (StringUtils.isEmpty(bestValuePlanId)) {
                final String failurePageUrl = request.getPageUrlDetails().getFailurePageUrl();
                SessionResponse.SessionData response = SessionResponse.SessionData.builder().redirectUrl(failurePageUrl).sid(id).build();
                return WynkResponseEntity.<SessionResponse.SessionData>builder().data(response).build();
            }
            final URIBuilder queryBuilder = new URIBuilder(PAYMENT_OPTION_URL);
            final PaymentCachingService cache = BeanLocatorFactory.getBean(PaymentCachingService.class);
            if (request.getProductDetails().getType().equalsIgnoreCase(PLAN)) {
                queryBuilder.addParameter(PLAN_ID, bestValuePlanId);
            } else {
                final ItemDTO item = cache.getItem(request.getProductDetails().getId());
                queryBuilder.addParameter(TITLE, item.getName());
                queryBuilder.addParameter(SUBTITLE, item.getName());
                queryBuilder.addParameter(ITEM_ID, item.getId());
                queryBuilder.addParameter(POINT_PURCHASE_ITEM_PRICE, String.valueOf(item.getPrice()));
            }
            queryBuilder.addParameter(UID,
                                      IdentityUtils.getUidFromUserName(request.getUserDetails().getMsisdn(),
                                                                       request.getAppDetails().getService()));
            queryBuilder.addParameter(APP_ID, String.valueOf(request.getAppDetails().getAppId()));
            queryBuilder.addParameter(SERVICE, String.valueOf(request.getAppDetails().getService()));
            queryBuilder.addParameter(BUILD_NO, String.valueOf(request.getAppDetails().getBuildNo()));
            queryBuilder.addParameter(DEVICE_ID_SHORT, String.valueOf(request.getAppDetails().getDeviceId()));
            queryBuilder.addParameter(BUILD_NO, String.valueOf(request.getAppDetails().getBuildNo()));
            if (Objects.nonNull(request.getMiscellaneousDetails())) {
                queryBuilder.addParameter(INGRESS_INTENT, String.valueOf(request.getMiscellaneousDetails().getIngressIntent()));
                if (request.getMiscellaneousDetails().isMandate()) {
                    queryBuilder.addParameter(PAYMENT_FLOW, PAYMENT_FLOW_MANDATE);
                } else if (request.getMiscellaneousDetails().isTrialOpted()) {
                    queryBuilder.addParameter(PAYMENT_FLOW, PAYMENT_FLOW_TRIAL_OPTED);
                } else if (request.getMiscellaneousDetails().isAutoRenew() && !request.getMiscellaneousDetails().isTrialOpted()) {
                    queryBuilder.addParameter(PAYMENT_FLOW, PAYMENT_FLOW_AUTO_RENEW);
                }
            }
            PlanDTO planDto = cache.getPlan(request.getProductDetails().getId());
            if (Objects.nonNull(planDto.getSku()) && Objects.nonNull(planDto.getSku().get("google_iap"))) {
                queryBuilder.addParameter(PaymentConstants.SKU_ID, planDto.getSku().get("google_iap"));
            }
            String builder = PAYMENT_OPTION_URL + id + SLASH + request.getOs() + QUESTION_MARK + queryBuilder.build().getQuery();
            SessionResponse.SessionData response = SessionResponse.SessionData.builder().redirectUrl(builder).sid(id).build();
            return WynkResponseEntity.<SessionResponse.SessionData>builder().data(response).build();
        } catch (URISyntaxException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY997, e);
        }
    }

}
