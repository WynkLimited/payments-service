package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.adapter.SessionDTOAdapter;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.SessionRequest;
import in.wynk.common.dto.SessionResponse;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.service.IPointPurchaseSessionService;
import in.wynk.session.dto.Session;
import in.wynk.session.service.ISessionManager;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.*;

@Service
public class PointPurchaseSessionServiceImpl implements IPointPurchaseSessionService {

    private final ISessionManager sessionManager;
    private final ClientDetailsCachingService clientDetailsCachingService;
    @Value("${session.duration:15}")
    private Integer duration;
    @Value("${payment.payOption.page}")
    private String PAYMENT_OPTION_URL;

    public PointPurchaseSessionServiceImpl(ISessionManager sessionManager, ClientDetailsCachingService clientDetailsCachingService) {
        this.sessionManager = sessionManager;
        this.clientDetailsCachingService = clientDetailsCachingService;
    }

    @Override
    public SessionResponse initSession(SessionRequest request) {
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        ClientDetails clientDetails = (ClientDetails) clientDetailsCachingService.getClientById(clientId);
        try {
            AnalyticService.update(CLIENT, clientDetails.getAlias());
            SessionDTO sessionDTO = SessionDTOAdapter.generateSessionDTO(request);
            sessionDTO.put(CLIENT, clientDetails.getAlias());
            Session<SessionDTO> session = sessionManager.init(sessionDTO, duration, TimeUnit.MINUTES);
            URIBuilder queryBuilder = new URIBuilder(PAYMENT_OPTION_URL);
            if (request.getParams() != null) {
                queryBuilder.addParameter(TITLE, request.getParams().get(TITLE));
                queryBuilder.addParameter(SUBTITLE, request.getParams().get(SUBTITLE));
                queryBuilder.addParameter(CLIENT, request.getParams().get(CLIENT));
            }
            queryBuilder.addParameter(ITEM_ID, request.getItemId());
            queryBuilder.addParameter(POINT_PURCHASE_FLOW, Boolean.TRUE.toString());
            queryBuilder.addParameter(AMOUNT, String.valueOf(request.getItemPrice()));
            String builder = PAYMENT_OPTION_URL + session.getId().toString() + SLASH + request.getOs().getValue() + QUESTION_MARK + queryBuilder.build().getQuery();
            SessionResponse.SessionData response = SessionResponse.SessionData.builder().redirectUrl(builder).sid(session.getId().toString()).build();
            return SessionResponse.builder().data(response).build();
        } catch (URISyntaxException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY997);
        }
    }
}
