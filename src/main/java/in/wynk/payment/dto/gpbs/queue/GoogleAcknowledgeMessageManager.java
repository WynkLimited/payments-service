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
@WynkQueue(queueName = "${payment.googlePlay.queue.provisioning.name}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER, quartz = @WynkQueue.QuartzConfiguration(expression = "T(java.util.Arrays).asList(0, 2, 4, 6, 8, 9 , 10, 11, 12, 13, 6).get(#n)", publishUntil = 10, publishUntilUnit = TimeUnit.HOURS))
public class GoogleAcknowledgeMessageManager implements MessageToEventMapper<GooglePlayMessageThresholdEvent> {
    @Analysed
    private String url;

    @Analysed
    private HttpHeaders headers;

    @Analysed
    private GooglePlayAcknowledgeRequest body;

    @Override
    public GooglePlayMessageThresholdEvent map () {
        return GooglePlayMessageThresholdEvent.builder()
                .maxAttempt(10).type("ACKNOWLEDGE").build();
               // headers(getHeaders()).url(this.url);
    }
}
