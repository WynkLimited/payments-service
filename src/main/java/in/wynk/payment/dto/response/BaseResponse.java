package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.core.constant.PaymentConstants;
import lombok.*;
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

import static in.wynk.common.constant.BaseConstants.HTTP_STATUS;

@Getter
@Builder
@ToString
@AnalysedEntity
@RequiredArgsConstructor
public class BaseResponse<R> {

    @Analysed
    private final R body;
    private final HttpStatus status;
    private final HttpHeaders headers;

    public ResponseEntity<R> getResponse() {
        return new ResponseEntity<>(body, headers, status);
    }

    public static BaseResponse<Void> redirectResponse(String location, List<NameValuePair> nvps) throws URISyntaxException {
        URI uri = new URIBuilder(location).addParameters(nvps).build();
        return redirectResponse(uri);
    }

    public static BaseResponse<Void> redirectResponse(URI uri) {
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.LOCATION, uri.toString());
        AnalyticService.update(HTTP_STATUS, HttpStatus.FOUND.value());
        AnalyticService.update(HttpHeaders.LOCATION, uri.toString());
        return BaseResponse.<Void>builder().headers(headers).status(HttpStatus.FOUND).build();
    }

    @SneakyThrows
    public static BaseResponse<Void> redirectResponse(String location) {
        return redirectResponse(location, Collections.emptyList());
    }

    public static BaseResponse<Object> status(boolean success){
        Map<String, Boolean> status =  new HashMap<>();
        status.put(PaymentConstants.SUCCESS, success);
        return BaseResponse.builder().body(status).status(HttpStatus.OK).build();
    }

}
