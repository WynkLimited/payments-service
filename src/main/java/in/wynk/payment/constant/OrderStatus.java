package in.wynk.payment.constant;

/**
 * @author Nishesh Pandey
 */
public enum OrderStatus {
    ORDER_CREATED,
    ORDER_AWAITING_EVENT,
    ORDER_PROCESSING,
    ORDER_COMPLETE,
    ORDER_MANUAL_COMPLETE,
    ORDER_PARTIAL_COMPLETE,
    ORDER_FAILED,
    ORDER_CLOSED;
}
