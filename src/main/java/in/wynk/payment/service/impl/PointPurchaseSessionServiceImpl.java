package in.wynk.payment.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.adapter.SessionDTOAdapter;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.SessionRequest;
import in.wynk.common.dto.SessionResponse;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.country.core.service.CountryCurrencyDetailsCachingService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.service.IPointPurchaseSessionService;
import in.wynk.session.constant.SessionConstant;
import in.wynk.session.service.ISessionManager;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang.StringUtils;
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
public class PointPurchaseSessionServiceImpl implements IPointPurchaseSessionService {

    @Value("${session.duration:15}")
    private Integer duration;
    @Value("${payment.payOption.page}")
    private String PAYMENT_OPTION_URL;

    private final ISessionManager<String, SessionDTO> sessionManager;
    private final ClientDetailsCachingService clientDetailsCachingService;

    @Override
    public SessionResponse initSession(SessionRequest request) {
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        ClientDetails clientDetails = (ClientDetails) clientDetailsCachingService.getClientById(clientId);
        try {
            AnalyticService.update(CLIENT, clientDetails.getAlias());
            SessionDTO sessionDTO = SessionDTOAdapter.generateSessionDTO(request);
            sessionDTO.put(CLIENT, clientDetails.getAlias());
            CountryCurrencyDetailsCachingService countryCurrencyDetailsCachingService = BeanLocatorFactory.getBean(CountryCurrencyDetailsCachingService.class);
            if(StringUtils.isNotEmpty(request.getCountryCode()) && countryCurrencyDetailsCachingService.containsKey(request.getCountryCode())) {
                sessionDTO.put(CURRENCY,countryCurrencyDetailsCachingService.get(request.getCountryCode()).getCurrency());
            } else {
                sessionDTO.put(CURRENCY,countryCurrencyDetailsCachingService.get(WynkServiceUtils.fromServiceId(request.getService()).getDefaultCountryCode()).getCurrency());
            }
            final String id = UUIDs.timeBased().toString();
            sessionManager.init(SessionConstant.SESSION_KEY + SessionConstant.COLON_DELIMITER + id, sessionDTO, duration, TimeUnit.MINUTES);
            AnalyticService.update(SESSION_ID, id);
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
}
