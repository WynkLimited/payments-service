package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Stream;
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
public class PaymentDump {
    Stream<Transaction> transactions;
}
