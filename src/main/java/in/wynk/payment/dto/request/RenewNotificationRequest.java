package in.wynk.payment.dto.request;
import lombok.Data;
@Data
public class RenewNotificationRequest {
    private int offsetDay;
    private int offsetHour;
    private int preOffsetDay;
}
