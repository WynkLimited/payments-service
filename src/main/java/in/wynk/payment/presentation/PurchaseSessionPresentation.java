package in.wynk.payment.presentation;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.SessionResponse;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.PointDetails;
import in.wynk.payment.dto.PurchaseRequest;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.data.util.Pair;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.net.URISyntaxException;
import java.util.Objects;
import java.util.List;

import static in.wynk.payment.core.constant.PaymentConstants.*;

@Component
public class PurchaseSessionPresentation implements IPresentation<WynkResponseEntity<SessionResponse.SessionData>, Pair<String, PurchaseRequest>> {

    @Value("show.GBP.disabled.plans")
    private List<String> showGBPDisabledPlans;

    @Override
    public WynkResponseEntity<SessionResponse.SessionData> transform(Pair<String, PurchaseRequest> pair) {
        final String id = pair.getFirst();
        final PurchaseRequest request = pair.getSecond();
        final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        final Client client = BeanLocatorFactory.getBean(ClientDetailsCachingService.class).getClientById(clientId);
        final String PAYMENT_OPTION_URL = EmbeddedPropertyResolver.resolveEmbeddedValue(PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c", client.getAlias()).replace("%p", "payOption"), "${payment.payOption.page}");;
        try {
            final URIBuilder queryBuilder = new URIBuilder(PAYMENT_OPTION_URL);
            final PaymentCachingService cache = BeanLocatorFactory.getBean(PaymentCachingService.class);
            if (request.getProductDetails().getType().equalsIgnoreCase(PLAN)) {
                queryBuilder.addParameter(PLAN_ID, request.getProductDetails().getId());
                PlanDTO planDto = cache.getPlan(request.getProductDetails().getId());
                if (Objects.nonNull(planDto.getSku()) && Objects.nonNull(planDto.getSku().get("google_iap")) && !showGBPDisabledPlans.contains(request.getProductDetails().getId())) {
                    queryBuilder.addParameter(PaymentConstants.SKU_ID, planDto.getSku().get("google_iap"));
                } else {
                    queryBuilder.addParameter(PaymentConstants.SHOW_GPB, String.valueOf(false));
                }
            } else {
                PointDetails pointDetails = (PointDetails) request.getProductDetails();
                queryBuilder.addParameter(ITEM_ID, pointDetails.getItemId());
                queryBuilder.addParameter(TITLE, pointDetails.getTitle());
                queryBuilder.addParameter(SKU_ID, pointDetails.getSkuId());
            }
            queryBuilder.addParameter(UID, IdentityUtils.getUidFromUserName(request.getUserDetails().getMsisdn(), request.getAppDetails().getService()));
            queryBuilder.addParameter(APP_ID, String.valueOf(request.getAppDetails().getAppId()));
            queryBuilder.addParameter(SERVICE, String.valueOf(request.getAppDetails().getService()));
            queryBuilder.addParameter(BUILD_NO, String.valueOf(request.getAppDetails().getBuildNo()));
            queryBuilder.addParameter(DEVICE_ID_SHORT, String.valueOf(request.getAppDetails().getDeviceId()));
            if(Objects.nonNull(request.getMiscellaneousDetails())) {
                queryBuilder.addParameter(INGRESS_INTENT, String.valueOf(request.getMiscellaneousDetails().getIngressIntent()));
                if (request.getMiscellaneousDetails().isMandate()) {
                    queryBuilder.addParameter(PAYMENT_FLOW, PAYMENT_FLOW_MANDATE);
                } else if (request.getMiscellaneousDetails().isTrialOpted()) {
                    queryBuilder.addParameter(PAYMENT_FLOW, PAYMENT_FLOW_TRIAL_OPTED);
                } else if (request.getMiscellaneousDetails().isAutoRenew() && !request.getMiscellaneousDetails().isTrialOpted()) {
                    queryBuilder.addParameter(PAYMENT_FLOW, PAYMENT_FLOW_AUTO_RENEW);
                }
            }

            String builder = PAYMENT_OPTION_URL + id + SLASH + request.getOs() + QUESTION_MARK + queryBuilder.build().getQuery();
            SessionResponse.SessionData response = SessionResponse.SessionData.builder().redirectUrl(builder).sid(id).build();
            return WynkResponseEntity.<SessionResponse.SessionData>builder().data(response).build();
        } catch (URISyntaxException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY997, e);
        }
    }
}
