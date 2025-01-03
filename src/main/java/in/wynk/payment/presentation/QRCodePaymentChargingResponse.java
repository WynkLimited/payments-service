package in.wynk.payment.presentation;

import in.wynk.common.utils.EmbeddedPropertyResolver;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.data.dto.IEntityCacheService;
import in.wynk.payment.constant.FlowType;
import in.wynk.payment.core.constant.PaymentChargingAction;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.PollingConfig;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.gateway.upi.UpiIntentChargingResponse;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.payment.dto.request.S2SChargingRequestV2;
import in.wynk.payment.dto.request.WebRequestVersionConversion;
import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import in.wynk.payment.presentation.dto.qrCode.QRCodeChargingResponse;
import in.wynk.payment.presentation.dto.qrCode.upi.QRCodeIntentSeamlessUpiPaymentChargingResponse;
import in.wynk.payment.presentation.dto.qrCode.upi.SeamlessUpiPaymentChargingResponse;
import in.wynk.payment.presentation.dto.qrCode.upi.UpiPaymentChargingResponse;
import in.wynk.queue.dto.Payment;
import in.wynk.session.context.SessionContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.constant.FlowType.*;
import static in.wynk.payment.constant.UpiConstants.UPI_PREFIX;
import static in.wynk.payment.core.constant.PaymentConstants.APP_PACKAGE;
import static in.wynk.payment.dto.aps.common.ApsConstant.PAYMENT_STATUS_POLL_KEY;
import static in.wynk.payment.dto.aps.common.ApsConstant.PAYMENT_TIMER_KEY;

@Slf4j
@Service
@RequiredArgsConstructor
public class QRCodePaymentChargingResponse implements IPaymentPresentationV2<QRCodeChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>> {

    @Value("${payment.encKey}")
    private String ENC_KEY;

    private static final Logger logger = LoggerFactory.getLogger(WebRequestVersionConversion.class);

    private final IEntityCacheService<PaymentMethod, String> paymentMethodCache;
    private final Map<FlowType, IPaymentPresentationV2<? extends QRCodeChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>>> delegate = new HashMap<>();

    @PostConstruct
    public void init() {
        delegate.put(UPI, new QRCodePaymentChargingResponse.UpiChargingPresentation());
    }

    @SneakyThrows
    @Override
    public QRCodeChargingResponse transform(Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse> payload) {
        final PaymentMethod method = paymentMethodCache.get(payload.getFirst().getPaymentDetails().getPaymentId());
        return delegate.get(FlowType.valueOf(method.getGroup().toUpperCase())).transform(payload);
    }

    private class UpiChargingPresentation implements IPaymentPresentationV2<UpiPaymentChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>> {

        private final Map<FlowType, IPaymentPresentationV2<? extends UpiPaymentChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>>> upiDelegate = new HashMap<>();

        public UpiChargingPresentation() {
            upiDelegate.put(SEAMLESS, new QRCodePaymentChargingResponse.UpiChargingPresentation.UpiSeamless());
        }

        @SneakyThrows
        @Override
        public UpiPaymentChargingResponse transform(Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse> payload) {
            String flowType = paymentMethodCache.get(payload.getFirst().getPaymentDetails().getPaymentId()).getFlowType();
            return upiDelegate.get(FlowType.valueOf(flowType)).transform(payload);
        }

        public class UpiSeamless implements IPaymentPresentationV2<SeamlessUpiPaymentChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>> {

            private final Map<FlowType, IPaymentPresentationV2<? extends SeamlessUpiPaymentChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>>> upiSeamlessDelegate = new HashMap<>();

            public UpiSeamless() {
                upiSeamlessDelegate.put(INTENT, new QRCodePaymentChargingResponse.UpiChargingPresentation.UpiSeamless.UpiSeamlessIntent());
            }

            @SneakyThrows
            @Override
            public SeamlessUpiPaymentChargingResponse transform(Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse> payload) {
                final Payment payment = payload.getSecond().getClass().getAnnotation(Payment.class);
                return upiSeamlessDelegate.get(FlowType.valueOf(payment.mode())).transform(payload);
            }

            public class UpiSeamlessIntent implements IPaymentPresentationV2<QRCodeIntentSeamlessUpiPaymentChargingResponse, Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse>> {
                @SneakyThrows
                @Override
                public QRCodeIntentSeamlessUpiPaymentChargingResponse transform(Pair<AbstractPaymentChargingRequest, AbstractPaymentChargingResponse> payload) {
                    AbstractPaymentChargingRequest request = payload.getFirst();
                    UpiIntentChargingResponse response = (UpiIntentChargingResponse) payload.getSecond();
                    PaymentMethod paymentMethod = paymentMethodCache.get(request.getPaymentDetails().getPaymentId());

                    final String upiPrefix = (String) paymentMethod.getMeta().getOrDefault(UPI_PREFIX, "upi");
                    String intentBasePath = request.isAutoRenewOpted() ? "mandate" : "pay";

                    String intentUrl = buildIntentUrl(upiPrefix, intentBasePath, request, response);
                    logger.info("Intent URL to be sent to client: {}", intentUrl);

                    String encryptedDeepLink = EncryptionUtils.encrypt(intentUrl, ENC_KEY);
                    long expiryTtl = calculateQrExpiryTime();

                    return QRCodeIntentSeamlessUpiPaymentChargingResponse.builder()
                            .deepLink(encryptedDeepLink)
                            .action(PaymentChargingAction.INTENT.getAction())
                            .expiryTtl(expiryTtl)
                            .appPackage((String) paymentMethod.getMeta().get(APP_PACKAGE))
                            .pollingConfig(buildPollingConfig(request.getPaymentId(), S2SChargingRequestV2.class.isAssignableFrom(request.getClass())))
                            .build();
                }

                private String buildIntentUrl(String upiPrefix, String basePath, AbstractPaymentChargingRequest request, UpiIntentChargingResponse response) {
                    Map<String, String> queryParams = new LinkedHashMap<>();
                    queryParams.put("pa", response.getPa());
                    queryParams.put("pn", response.getPn());
                    queryParams.put("tr", response.getTr());
                    queryParams.put("am", response.getAm());
                    queryParams.put("cu", response.getCu());
                    queryParams.put("tn", response.getTn());
                    queryParams.put("mc", response.getMc());
                    queryParams.put("tid", response.getTid().replaceAll("-", ""));

                    if (request.isAutoRenewOpted()) {
                        queryParams.putAll(getAutoRenewParams(response));
                    }

//                    long currentTimeStamp = System.currentTimeMillis();
//                    queryParams.put("qrts", String.valueOf(currentTimeStamp));
//                    queryParams.put("qrExpire", String.valueOf(calculateQrExpiryTime()));
                    queryParams.put("expiresIn", String.valueOf(calculateQrExpiryTime()));

                    String baseIntentUrl = String.format("%s://%s?", upiPrefix, basePath);
                    return queryParams.entrySet()
                            .stream()
                            .map(entry -> entry.getKey() + "=" + entry.getValue())
                            .collect(Collectors.joining("&", baseIntentUrl, ""));
                }

                private Map<String, String> getAutoRenewParams(UpiIntentChargingResponse response) {
                    Map<String, String> params = new LinkedHashMap<>();
                    putIfNotEmpty(params, "mn", response.getMn());
                    putIfNotEmpty(params, "rev", response.getRev());
                    putIfNotEmpty(params, "mode", response.getMode());
                    putIfNotEmpty(params, "recur", response.getRecur());
                    putIfNotEmpty(params, "orgid", response.getOrgId());
                    putIfNotEmpty(params, "block", response.getBlock());
                    putIfNotEmpty(params, "amrule", response.getAmRule());
                    putIfNotEmpty(params, "purpose", response.getPurpose());
                    putIfNotEmpty(params, "txnType", response.getTxnType());
                    putIfNotEmpty(params, "recurtype", response.getRecurType());
                    putIfNotEmpty(params, "recurvalue", response.getRecurValue());
                    putIfNotEmpty(params, "validityend", response.getValidityEnd());
                    putIfNotEmpty(params, "validitystart", response.getValidityStart());
                    return params;
                }

                private void putIfNotEmpty(Map<String, String> map, String key, String value) {
                    if (!StringUtils.isEmpty(value)) {
                        map.put(key, value);
                    }
                }

                private long calculateQrExpiryTime() {
                    Calendar calendar = Calendar.getInstance();
                    calendar.add(Calendar.MINUTE, 1);
                    return calendar.getTimeInMillis();
                }
            }
        }
    }

    private PollingConfig buildPollingConfig(String payId, boolean isS2S) {
        final Map<String, Object> meta = paymentMethodCache.get(payId).getMeta();
        final long timer = ((Double) meta.getOrDefault(PAYMENT_TIMER_KEY, 40.0)).longValue();
        final long interval = ((Double) meta.getOrDefault(PAYMENT_STATUS_POLL_KEY, 10.0)).longValue();
        final StringBuilder pollingEndpoint = new StringBuilder();
        if (!isS2S)
            pollingEndpoint.append(EmbeddedPropertyResolver.resolveEmbeddedValue("${service.payment.api.endpoint.v2.poll}")).append(SessionContextHolder.getId());
        else
            pollingEndpoint.append(EmbeddedPropertyResolver.resolveEmbeddedValue("${service.payment.api.endpoint.v3.pollS2S}")).append(TransactionContext.get().getIdStr());
        return PollingConfig.builder().interval(interval).frequency(timer / interval).timeout(timer).endpoint(pollingEndpoint.toString()).build();
    }
}