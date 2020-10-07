package in.wynk.payment.service.impl;

import in.wynk.commons.adapter.SessionDTOAdapter;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.dto.SessionRequest;
import in.wynk.commons.dto.SessionResponse;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.service.ISessionService;
import in.wynk.session.dto.Session;
import in.wynk.session.service.ISessionManager;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URISyntaxException;
import java.util.concurrent.TimeUnit;

import static in.wynk.commons.constants.BaseConstants.QUESTION_MARK;
import static in.wynk.commons.constants.BaseConstants.SLASH;

@Service
public class SessionServiceImpl implements ISessionService {

    @Value("${session.duration:15}")
    private Integer duration;

    @Value("${payment.payOption.page}")
    private String PAYMENT_OPTION_URL;

    private final ISessionManager sessionManager;

    public SessionServiceImpl(ISessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public SessionResponse initSession(SessionRequest request) {
        try {
            SessionDTO sessionDTO = SessionDTOAdapter.generateSessionDTO(request);
            Session<SessionDTO> session = sessionManager.init(sessionDTO, duration, TimeUnit.MINUTES);
            URIBuilder queryBuilder = new URIBuilder(PAYMENT_OPTION_URL + session.getId().toString() + SLASH + request.getOs().getValue());
            request.getParams().forEach((key, value) -> queryBuilder.addParameter(key, value));
            StringBuilder builder = new StringBuilder(PAYMENT_OPTION_URL);
            builder.append(session.getId().toString())
                    .append(SLASH)
                    .append(request.getOs().getValue())
                    .append(QUESTION_MARK)
                    .append(queryBuilder.build().getQuery());
            SessionResponse.SessionData response = SessionResponse.SessionData.builder().redirectUrl(builder.toString()).sid(session.getId().toString()).build();
            return SessionResponse.builder().data(response).build();
        } catch (URISyntaxException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY997);
        }
    }
}
