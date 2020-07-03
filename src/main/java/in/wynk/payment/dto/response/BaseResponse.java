package in.wynk.payment.dto.response;

import lombok.*;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@Getter
@Builder
@RequiredArgsConstructor
@ToString
public class BaseResponse<T> {

    private final T body;
    private final HttpStatus status;
    private final HttpHeaders headers;

    public ResponseEntity<T> getResponse() {
        return new ResponseEntity<>(body, headers, status);
    }

    public static BaseResponse<Void> redirectResponse(String url){
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, url);
        return BaseResponse.<Void>builder().status(HttpStatus.FOUND).headers(headers).build();
    }

}
