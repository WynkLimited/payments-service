package in.wynk.payment.dto.response;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.ToString;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Getter
@Builder
@RequiredArgsConstructor
@ToString
public class BaseResponse<R> {

    private final R body;
    private final HttpStatus status;
    private final HttpHeaders headers;

    public ResponseEntity<R> getResponse() {
        return new ResponseEntity<>(body, headers, status);
    }

    public static BaseResponse<Void> redirectResponse(String location, List<NameValuePair> nvps) throws URISyntaxException {
        HttpHeaders headers = new HttpHeaders();
        URI uri = new URIBuilder(location).addParameters(nvps).build();
        headers.add(HttpHeaders.LOCATION, uri.toString());
        return BaseResponse.<Void>builder().headers(headers).status(HttpStatus.FOUND).build();
    }

    @SneakyThrows
    public static BaseResponse<Void> redirectResponse(String location) {
        return redirectResponse(location, Collections.emptyList());
    }

    public static BaseResponse<Object> status(boolean success){
        Map<String, Boolean> status =  new HashMap<>();
        status.put("success", success);
        return BaseResponse.builder().body(status).status(HttpStatus.OK).build();
    }

}
