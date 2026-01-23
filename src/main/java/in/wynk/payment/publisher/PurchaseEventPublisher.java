package in.wynk.payment.publisher;

import in.wynk.common.dto.SessionResponse;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.PurchaseRequest;
import in.wynk.payment.event.PurchaseRequestKafkaMessage;
import in.wynk.stream.service.DataPlatformKafkaService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseEventPublisher {

    private final DataPlatformKafkaService dataPlatformKafkaService;

    @Async
    public void publishAsync(PurchaseRequest request, WynkResponseEntity<SessionResponse.SessionData> response) {
        try {
            dataPlatformKafkaService.publish(PurchaseRequestKafkaMessage.from(request, response));
        } catch (Exception ex) {
            log.error("Kafka publish failed for purchase request sid={}", response, ex);
        }
    }
}