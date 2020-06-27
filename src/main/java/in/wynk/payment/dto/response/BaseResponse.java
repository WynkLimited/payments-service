package in.wynk.payment.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Getter
@Builder
@RequiredArgsConstructor
public class BaseResponse<R> {

    private final R body;
    private final HttpStatus status;
    private final HttpHeaders headers;

    public ResponseEntity<R> getResponse() {
        return new ResponseEntity<>(body, headers, status);
    }

}
