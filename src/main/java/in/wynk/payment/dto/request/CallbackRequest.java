package in.wynk.payment.dto.request;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.util.MultiValueMap;

@Getter
@Builder
@RequiredArgsConstructor
public class CallbackRequest<T> {

    private final String transactionId;
    private final T body;

}
