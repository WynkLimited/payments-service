package in.wynk.payment.gateway.aps.service;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.Currency;
import in.wynk.payment.dto.aps.common.LOB;
import in.wynk.payment.dto.aps.common.OrderInfo;
import in.wynk.payment.dto.aps.request.order.ApsOrderRequest;
import in.wynk.payment.dto.aps.common.ChannelInfo;
import in.wynk.payment.dto.aps.request.order.OrderItem;
import in.wynk.payment.dto.aps.common.UserInfo;
import in.wynk.payment.dto.request.AbstractRechargeOrderRequest;
import in.wynk.payment.dto.response.AbstractRechargeOrderResponse;
import in.wynk.payment.dto.response.RechargeOrderResponse;
import in.wynk.payment.gateway.IRechargeOrder;
import org.springframework.http.HttpMethod;

import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * @author Nishesh Pandey
 */
public class ApsOrderGatewayServiceImpl implements IRechargeOrder<AbstractRechargeOrderResponse, AbstractRechargeOrderRequest> {

    private final ApsCommonGatewayService common;
    private final String ORDER_ENDPOINT;

    public ApsOrderGatewayServiceImpl (String orderEndpoint, ApsCommonGatewayService common) {
        this.common = common;
        this.ORDER_ENDPOINT = orderEndpoint;
    }

    @Override
    public AbstractRechargeOrderResponse order (AbstractRechargeOrderRequest request) {
        final Transaction transaction = TransactionContext.get();
        String mobileNumber = transaction.getMsisdn().replace("+91", "");
        List<OrderItem> items = Collections.singletonList(
                OrderItem.builder().sku(createSku(mobileNumber, transaction.getAmount())).description("unlimited_pack").lob(LOB.PREPAID).amount(transaction.getAmount())
                        .meta(OrderItem.OrderMeta.builder().serviceInstance(mobileNumber).build())
                        .build());
        ApsOrderRequest apsOrderRequest = ApsOrderRequest.builder()
                .orderInfo(OrderInfo.builder().orderAmount(transaction.getAmount()).currency(Currency.INR).build())
                .items(items)
                .userInfo(UserInfo.builder().communicationNo(mobileNumber).build())
                .channelInfo(ChannelInfo.builder().channelMeta(ChannelInfo.ChannelMeta.builder().text("Successful prepaid recharge").build()).build()).build();
        return common.exchange(transaction.getClientAlias(), ORDER_ENDPOINT, HttpMethod.POST, null, apsOrderRequest, RechargeOrderResponse.class);

    }

    private String createSku (String mobileNumber, double amount) {
        Random random = new Random();
        int num = random.nextInt(99) + 10;
        return LOB.PREPAID + "_" + amount + "_" + mobileNumber + "_" + num;
    }
}
