package in.wynk.payment.gateway.atb;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.UserSubscriptionStatusEvent;
import in.wynk.payment.dto.addtobill.AddToBillUserSubscriptionStatusTask;
import in.wynk.payment.service.impl.ATBUserSubscriptionStatusHandler;
import in.wynk.scheduler.task.dto.TaskDefinition;
import in.wynk.scheduler.task.service.ITaskScheduler;
import in.wynk.vas.client.dto.atb.UserSubscriptionStatusResponse;
import in.wynk.vas.client.service.CatalogueVasClientService;
import org.quartz.SimpleScheduleBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * @author Nishesh Pandey
 */
@Service
public class ATBUserSubscriptionDetailsGateway {

    @Autowired
    private CatalogueVasClientService catalogueVasClientService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private ITaskScheduler<TaskDefinition<?>> taskScheduler;

    public void getUserSubscriptionDetails (String si) {
        try {
            ResponseEntity<UserSubscriptionStatusResponse> data = catalogueVasClientService.getUserSubscriptionStatusResponse(si);
            eventPublisher.publishEvent(UserSubscriptionStatusEvent.builder().status(Objects.requireNonNull(data.getBody()).getSuccess()).si(si));
        } catch (Exception e) {
            throw new RuntimeException("Exception occurred while finding user subscription status from thanks for the si: " + si, e);
        }
    }

    public void scheduleAtbTask(Transaction transaction) {
        taskScheduler.schedule(TaskDefinition.builder()
                .handler(ATBUserSubscriptionStatusHandler.class)
                .entity(AddToBillUserSubscriptionStatusTask.builder()
                        .transactionId(transaction.getIdStr())
                        .paymentCode(String.valueOf(transaction.getPaymentChannel().getCode()))
                        .si(transaction.getMsisdn().replace("+91", ""))
                        .build())
                .triggerConfiguration(TaskDefinition.TriggerConfiguration.builder()
                        .durable(false)
                        .startAt(calendar.getTime())
                        .scheduleBuilder(SimpleScheduleBuilder.simpleSchedule().withIntervalInSeconds(0).withRepeatCount(0))
                        .build())
                .build());

    }
}
