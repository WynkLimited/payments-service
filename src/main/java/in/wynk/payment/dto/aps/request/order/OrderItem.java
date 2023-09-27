package in.wynk.payment.dto.aps.request.order;

import in.wynk.payment.dto.aps.common.LOB;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
public class OrderItem {
    private String sku;
    private String description;
    private LOB lob;
    private double amount;
    private OrderMeta meta;

    @Getter
    @Builder
    @ToString
    public static class OrderMeta {
        private String serviceInstance;
    }
}
