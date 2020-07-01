package in.wynk.payment.dto.response;

import lombok.Builder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Builder
@RequiredArgsConstructor
public class BaseResponse<T> {

    private final T body;
    private final HttpStatus status;
    private final HttpHeaders headers;

    public ResponseEntity<T> getResponse() {
        return new ResponseEntity<>(body, headers, status);
    }

}
