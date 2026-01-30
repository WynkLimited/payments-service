package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class UrlShortenResponse {
    @JsonProperty("url")
    private String tinyUrl;
}
