package in.wynk.payment.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.client.service.ClientDetailsCachingService;
import in.wynk.common.adapter.SessionDTOAdapter;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.SessionRequest;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.service.IDummySessionGenerator;
import in.wynk.session.constant.SessionConstant;
import in.wynk.session.dto.Session;
import in.wynk.session.service.ISessionManager;
import in.wynk.wynkservice.api.utils.WynkServiceUtils;
import in.wynk.wynkservice.core.dao.entity.App;
import in.wynk.wynkservice.core.dao.entity.Os;
import in.wynk.wynkservice.core.dao.entity.WynkService;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.session.constant.SessionConstant.SESSION_ID;

@Service
@RequiredArgsConstructor
public class DummySessionGeneratorImpl implements IDummySessionGenerator {

    @Value("${session.duration:15}")
    private Integer duration;

    private final ISessionManager<String, SessionDTO> sessionManager;
    private final ClientDetailsCachingService clientDetailsCachingService;

    @Override
    public IapVerificationRequest initSession(IapVerificationRequest request) {
        Map<String, Object> map = new HashMap<>();
        if (StringUtils.isAnyBlank(request.getUid(), request.getMsisdn())) {
            throw new WynkRuntimeException(WynkErrorType.UT001, "Invalid UID or MSISDN");
        }
        map.put(UID, request.getUid());
        map.put(MSISDN, request.getMsisdn());
        map.put(OS, WynkServiceUtils.fromOsId(request.getOs()).getId().toLowerCase());
        map.put(CLIENT, getClientAlias(SecurityContextHolder.getContext().getAuthentication().getName()));
        if (Objects.nonNull(request.getBuildNo())) {
            map.put(BUILD_NO, request.getBuildNo());
        }
        if (StringUtils.isNotBlank(request.getDeviceId())) {
            map.put(DEVICE_ID, request.getDeviceId());
        }
        if (StringUtils.isNotBlank(request.getService())) {
            map.put(SERVICE, request.getService());
        }
        SessionDTO sessionDTO = SessionDTO.builder().sessionPayload(map).build();
        final String id = UUIDs.timeBased().toString();
        sessionManager.init(SessionConstant.SESSION_KEY + SessionConstant.COLON_DELIMITER + id, sessionDTO, 5, TimeUnit.MINUTES);
        AnalyticService.update(SESSION_ID, id);
        request.setSid(id);
        return request;
    }

    @Override
    public Session<String, SessionDTO> generate(SessionRequest request) {
        String clientId = SecurityContextHolder.getContext().getAuthentication().getPrincipal().toString();
        ClientDetails clientDetails = (ClientDetails) clientDetailsCachingService.getClientById(clientId);
        try {
            AnalyticService.update(CLIENT, clientDetails.getAlias());
            SessionDTO dto = SessionDTOAdapter.generateSessionDTO(request);
            dto.put(CLIENT, clientDetails.getAlias());
            final String id = UUIDs.timeBased().toString();
            AnalyticService.update(SESSION_ID, id);
            return sessionManager.init(SessionConstant.SESSION_KEY + SessionConstant.COLON_DELIMITER + id, dto, duration, TimeUnit.MINUTES);
        } catch (Exception e) {
            throw new WynkRuntimeException("Unable to generate session");
        }
    }

    @ClientAware(clientId = "#clientId")
    private String getClientAlias(String clientId) {
        ClientDetails clientDetails = (ClientDetails) ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
        return clientDetails.getAlias();
    }

}
