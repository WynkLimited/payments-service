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
                    final PaymentMethod method = paymentMethodCache.get(payload.getFirst().getPaymentDetails().getPaymentId());

                    final String prefix = (String) method.getMeta().getOrDefault(UPI_PREFIX, "upi");
                    StringBuilder stringBuilder = new StringBuilder(prefix);
                    if (!request.isAutoRenewOpted()) stringBuilder.append("://pay?");
                    else {
                        stringBuilder.append("://mandate?");
                        if (!StringUtils.isEmpty(response.getMn()))
                            stringBuilder.append("&mn=").append(response.getMn());
                        if (!StringUtils.isEmpty(response.getRev()))
                            stringBuilder.append("&rev=").append(response.getRev());
                        if (!StringUtils.isEmpty(response.getMode()))
                            stringBuilder.append("&mode=").append(response.getMode());
                        if (!StringUtils.isEmpty(response.getRecur()))
                            stringBuilder.append("&recur=").append(response.getRecur());
                        if (!StringUtils.isEmpty(response.getOrgId()))
                            stringBuilder.append("&orgid=").append(response.getOrgId());
                        if (!StringUtils.isEmpty(response.getBlock()))
                            stringBuilder.append("&block=").append(response.getBlock());
                        if (!StringUtils.isEmpty(response.getAmRule()))
                            stringBuilder.append("&amrule=").append(response.getAmRule());
                        if (!StringUtils.isEmpty(response.getPurpose()))
                            stringBuilder.append("&purpose=").append(response.getPurpose());
                        if (!StringUtils.isEmpty(response.getTxnType()))
                            stringBuilder.append("&txnType=").append(response.getTxnType());
                        if (!StringUtils.isEmpty(response.getRecurType()))
                            stringBuilder.append("&recurtype=").append(response.getRecurType());
                        if (!StringUtils.isEmpty(response.getRecurValue()))
                            stringBuilder.append("&recurvalue=").append(response.getRecurValue());
                        if (!StringUtils.isEmpty(response.getValidityEnd()))
                            stringBuilder.append("&validityend=").append(response.getValidityEnd());
                        if (!StringUtils.isEmpty(response.getValidityStart()))
                            stringBuilder.append("&validitystart=").append(response.getValidityStart());
                        stringBuilder.append("&");
                    }
                    Calendar cal = Calendar.getInstance();
                    long curTimeStamp = cal.getTimeInMillis();
                    cal.add(Calendar.MINUTE, 5);
                    long qrExpireTimeStamp = cal.getTimeInMillis();

                    stringBuilder.append("pa=").append(response.getPa());
                    stringBuilder.append("&qrts=").append(curTimeStamp);
                    stringBuilder.append("&qrExpire=").append(qrExpireTimeStamp);
                    stringBuilder.append("&pn=").append(response.getPn());
                    stringBuilder.append("&tr=").append(response.getTr());
                    stringBuilder.append("&am=").append(response.getAm());
                    stringBuilder.append("&cu=").append(response.getCu());
                    stringBuilder.append("&tn=").append(response.getTn());
                    stringBuilder.append("&mc=").append(response.getMc());
                    stringBuilder.append("&tid=").append(response.getTid().replaceAll("-", ""));
                    logger.info("intentUrl to be send to client: {}", stringBuilder);
                    final String form = EncryptionUtils.encrypt(stringBuilder.toString(), ENC_KEY);
                    return QRCodeIntentSeamlessUpiPaymentChargingResponse.builder()
                            .deepLink(form)
                            .action(PaymentChargingAction.INTENT.getAction())
                            .expiryTtl(qrExpireTimeStamp)
                            .appPackage((String) method.getMeta().get(APP_PACKAGE))
                            .pollingConfig(buildPollingConfig(payload.getFirst().getPaymentId(), S2SChargingRequestV2.class.isAssignableFrom(payload.getFirst().getClass())))
                            .build();
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