package in.wynk.payment.service.impl;

import in.wynk.commons.adapter.SessionDTOAdapter;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.dto.SessionRequest;
import in.wynk.commons.dto.SessionResponse;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.service.IPointPurchaseSessionService;
import in.wynk.session.dto.Session;
import in.wynk.session.service.ISessionManager;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static in.wynk.commons.constants.BaseConstants.*;

@Service
public class PointPurchaseSessionServiceImpl implements IPointPurchaseSessionService {

    @Value("${session.duration:15}")
    private Integer duration;

    @Value("${payment.payOption.page}")
    private String PAYMENT_OPTION_URL;

    private final ISessionManager sessionManager;

    public PointPurchaseSessionServiceImpl(ISessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public SessionResponse initSession(SessionRequest request) {
        try {
            SessionDTO sessionDTO = SessionDTOAdapter.generateSessionDTO(request);
            Session<SessionDTO> session = sessionManager.init(sessionDTO, duration, TimeUnit.MINUTES);
            URIBuilder queryBuilder = new URIBuilder(PAYMENT_OPTION_URL);
            if (request.getParams() != null) {
                queryBuilder.addParameter(TITLE, request.getParams().getTitle());
                queryBuilder.addParameter(SUBTITLE, request.getParams().getSubtitle());
                queryBuilder.addParameter(CLIENT, request.getParams().getClient());
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
