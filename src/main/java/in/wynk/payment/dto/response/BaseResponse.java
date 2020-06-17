package in.wynk.payment.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Getter
@Builder
@RequiredArgsConstructor
public class BaseResponse<T> {

    private final T body;
    private final HttpStatus status;

    public ResponseEntity<T> getResponse() {
        return new ResponseEntity<>(body, status);
    }

}
