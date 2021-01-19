package in.wynk.payment.service.impl;

import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.client.context.ClientContext;
import in.wynk.client.core.constant.ClientErrorType;
import in.wynk.client.core.dao.entity.ClientDetails;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.Os;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.service.IDummySessionGenerator;
import in.wynk.session.dto.Session;
import in.wynk.session.service.ISessionManager;
import org.apache.commons.lang3.StringUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.*;

@Service
public class DummySessionGeneratorImpl implements IDummySessionGenerator {

    private final ISessionManager sessionManager;

    public DummySessionGeneratorImpl(ISessionManager sessionManager) {
        this.sessionManager = sessionManager;
    }

    @Override
    public IapVerificationRequest initSession(IapVerificationRequest request) {
        Map<String, Object> map = new HashMap<>();
        if (StringUtils.isAnyBlank(request.getUid(), request.getMsisdn())) {
            throw new WynkRuntimeException(WynkErrorType.UT001, "Invalid UID or MSISDN");
        }
        map.put(UID, request.getUid());
        map.put(MSISDN, request.getMsisdn());
        map.put(OS, Os.getOsFromValue(request.getOs()).getValue());
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
        Session<SessionDTO> session = sessionManager.init(sessionDTO, 5, TimeUnit.MINUTES);
        request.setSid(session.getId().toString());
        return request;
    }

    @ClientAware(clientId = "#clientId")
    private String getClientAlias(String clientId) {
        ClientDetails clientDetails = (ClientDetails) ClientContext.getClient().orElseThrow(() -> new WynkRuntimeException(ClientErrorType.CLIENT001));
        return clientDetails.getAlias();
    }

}
