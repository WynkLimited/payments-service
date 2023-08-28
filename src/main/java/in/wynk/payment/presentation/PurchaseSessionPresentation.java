package in.wynk.payment.presentation;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.IPresentation;
import in.wynk.common.dto.SessionResponse;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.PurchaseRequest;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.ItemDTO;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.data.util.Pair;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.net.URISyntaxException;

import static in.wynk.common.constant.BaseConstants.*;

@Component
public class PurchaseSessionPresentation implements IPresentation<WynkResponseEntity<SessionResponse.SessionData>, Pair<String, PurchaseRequest>> {
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
            } else {
                final ItemDTO item = cache.getItem(request.getProductDetails().getId());
                queryBuilder.addParameter(TITLE, item.getName());
                queryBuilder.addParameter(SUBTITLE, item.getName());
                queryBuilder.addParameter(ITEM_ID, item.getId());
                queryBuilder.addParameter(POINT_PURCHASE_ITEM_PRICE, String.valueOf(item.getPrice()));
            }
            queryBuilder.addParameter(UID, IdentityUtils.getUidFromUserName(request.getUserDetails().getMsisdn(), request.getAppDetails().getService()));
            queryBuilder.addParameter(APP_ID, String.valueOf(request.getAppDetails().getAppId()));
            queryBuilder.addParameter(SERVICE, String.valueOf(request.getAppDetails().getService()));
            queryBuilder.addParameter(BUILD_NO, String.valueOf(request.getAppDetails().getBuildNo()));
            queryBuilder.addParameter(DEVICE_ID_SHORT, String.valueOf(request.getAppDetails().getDeviceId()));
            queryBuilder.addParameter(BUILD_NO, String.valueOf(request.getAppDetails().getBuildNo()));
            queryBuilder.addParameter(INGRESS_INTENT,String.valueOf(request.getMiscellaneousDetails().getIngressIntent()));
            String builder = PAYMENT_OPTION_URL + id + SLASH + request.getOs() + QUESTION_MARK + queryBuilder.build().getQuery();
            SessionResponse.SessionData response = SessionResponse.SessionData.builder().redirectUrl(builder).sid(id).build();
            return WynkResponseEntity.<SessionResponse.SessionData>builder().data(response).build();
        } catch (URISyntaxException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY997, e);
        }
    }
}
