package in.wynk.payment.dto.gpbs.queue;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.event.GooglePlayMessageThresholdEvent;
import in.wynk.payment.dto.gpbs.request.GooglePlayAcknowledgeRequest;
import in.wynk.queue.dto.MessageToEventMapper;
import in.wynk.queue.dto.ProducerType;
import in.wynk.queue.dto.WynkQueue;
import lombok.*;
import org.springframework.http.HttpHeaders;

import java.util.concurrent.TimeUnit;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@AnalysedEntity
@WynkQueue(queueName = "${payment.googlePlay.queue.provisioning.name}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER, quartz = @WynkQueue.QuartzConfiguration(expression = "T(java.util.Arrays).asList(60, 60, 60, 60, 60, 300, 300, 300, 890, 890, 2400, 3600, 79200, 172800, 179800).get(#n)",publishUntil  = 3, publishUntilUnit = TimeUnit.DAYS))
public class GoogleAcknowledgeMessageManager implements MessageToEventMapper<GooglePlayMessageThresholdEvent> {
    @Analysed
    private String url;

    @Analysed
    private String service;

    @Analysed
    private GooglePlayAcknowledgeRequest body;

    @Override
    public GooglePlayMessageThresholdEvent map () {
        return GooglePlayMessageThresholdEvent.builder()
                .maxAttempt(10).type("ACKNOWLEDGE")
                .url(url)
                .service(service).build();
    }
}
