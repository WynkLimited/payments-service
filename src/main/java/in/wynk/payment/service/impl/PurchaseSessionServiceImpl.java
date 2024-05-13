package in.wynk.payment.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.SessionResponse;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.country.core.service.CountryCurrencyDetailsCachingService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.BestValuePlanPurchaseRequest;
import in.wynk.payment.dto.PurchaseRequest;
import in.wynk.payment.service.IPurchaseSessionService;
import in.wynk.session.constant.SessionConstant;
import in.wynk.session.service.ISessionManager;
import in.wynk.subscription.common.adapter.SessionDTOAdapter;
import in.wynk.subscription.common.request.SessionRequest;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import lombok.RequiredArgsConstructor;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.session.constant.SessionConstant.SESSION_ID;

@Service
@RequiredArgsConstructor
public class PurchaseSessionServiceImpl implements IPurchaseSessionService {

    private final ISessionManager<String, SessionDTO> sessionManager;
    private final ClientDetailsCachingService clientDetailsCachingService;
    private final CountryCurrencyDetailsCachingService countryCurrencyDetailsCachingService;

    @Value("${session.duration:15}")
    private Integer duration;

    @Override
    public SessionResponse initSession (SessionRequest request) {

        try {
            final String id = generate(request);
            final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
            final ClientDetails clientDetails = (ClientDetails) clientDetailsCachingService.getClientById(clientId);
            final String PAYMENT_OPTION_URL =
                    EmbeddedPropertyResolver.resolveEmbeddedValue(PaymentConstants.PAYMENT_PAGE_PLACE_HOLDER.replace("%c", clientDetails.getAlias()).replace("%p", "payOption"),
                            "${payment.payOption.page}");
            URIBuilder queryBuilder = new URIBuilder(PAYMENT_OPTION_URL);
            if (request.getParams() != null) {
                queryBuilder.addParameter(TITLE, request.getParams().get(TITLE));
                queryBuilder.addParameter(SUBTITLE, request.getParams().get(SUBTITLE));
                queryBuilder.addParameter(CLIENT, request.getParams().get(CLIENT));
            }
            queryBuilder.addParameter(ITEM_ID, request.getItemId());
            queryBuilder.addParameter(POINT_PURCHASE_FLOW, Boolean.TRUE.toString());
            queryBuilder.addParameter(AMOUNT, String.valueOf(request.getItemPrice()));
            String builder = PAYMENT_OPTION_URL + id + SLASH + request.getOs() + QUESTION_MARK + queryBuilder.build().getQuery();
            SessionResponse.SessionData response = SessionResponse.SessionData.builder().redirectUrl(builder).sid(id).build();
            return SessionResponse.builder().data(response).build();
        } catch (URISyntaxException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY997);
        }
    }

    @Override
    public String init (PurchaseRequest request) {
        return generate(request.toSession());
    }

    @Override
    public String init(final BestValuePlanPurchaseRequest request) {
        return generate(request.toSession());
    }

    private String generate (SessionRequest request) {
        final String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        final ClientDetails clientDetails = (ClientDetails) clientDetailsCachingService.getClientById(clientId);
        try {
            AnalyticService.update(CLIENT, clientDetails.getAlias());
            final SessionDTO sessionDTO = SessionDTOAdapter.generateSessionDTO(request);
            sessionDTO.put(CLIENT, clientDetails.getAlias());
            String countryCode = request.getCountryCode();
            if (org.apache.commons.lang.StringUtils.isEmpty(countryCode)) {
                countryCode = WynkServiceUtils.fromServiceId(request.getService()).getDefaultCountryCode();
                sessionDTO.put(COUNTRY_CODE, countryCurrencyDetailsCachingService.get(countryCode).getCountryCode());
            } else {
                sessionDTO.put(COUNTRY_CODE, countryCode);
            }

            final String id = UUIDs.timeBased().toString();
            sessionManager.init(SessionConstant.SESSION_KEY + SessionConstant.COLON_DELIMITER + id, sessionDTO, duration, TimeUnit.MINUTES);
            AnalyticService.update(SESSION_ID, id);
            return id;
        } catch (Exception ex) {
            throw new WynkRuntimeException("Unable to generate session url for purchase request due to", ex);
        }
    }
}