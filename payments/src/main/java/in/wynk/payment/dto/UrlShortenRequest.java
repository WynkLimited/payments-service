package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.common.utils.EmbeddedPropertyResolver;
import lombok.Builder;
import lombok.Getter;

@Getter
public class UrlShortenRequest {
    @JsonProperty("branch_key")
    private final String key;
    private final String campaign;
    private final String channel;
    private final UrlShortenData data;

    UrlShortenRequest(String key, String campaign, String channel, UrlShortenData data) {
        this.key = key;
        this.data = data;
        this.campaign = campaign;
        this.channel = channel;
    }

    public static UrlShortenRequestBuilder builder() {
        return new UrlShortenRequestBuilder();
    }

    @Getter
    @Builder
    public static class UrlShortenData {

        @JsonProperty("desktop_url")
        private String desktopPath;
        @JsonProperty("$android_deeplink_path")
        private String androidDeeplink;
        @JsonProperty("$fallback_url")
        private String fallbackUrl;
        @JsonProperty("$deeplink_path")
        private String path;

        public UrlShortenData(String androidDeeplink, String desktopPath, String fallbackUrl) {
            this.androidDeeplink = androidDeeplink;
            this.desktopPath = desktopPath;
            this.fallbackUrl = fallbackUrl;
        }

        public UrlShortenData(String androidDeeplink, String desktopPath, String fallbackUrl, String path) {
            this.androidDeeplink = androidDeeplink;
            this.desktopPath = desktopPath;
            this.fallbackUrl = fallbackUrl;
            this.path = path;
        }

        public UrlShortenData(String path) {
            this.path = path;
        }

    }

    public static class UrlShortenRequestBuilder {
        private String key;
        private String campaign;
        private String channel;
        private UrlShortenData data;

        UrlShortenRequestBuilder() {
            this.key = EmbeddedPropertyResolver.resolveEmbeddedValue("${branch.onelink.key}");
        }

        public UrlShortenRequestBuilder campaign(String campaign) {
            this.campaign = campaign;
            return this;
        }

        public UrlShortenRequestBuilder channel(String channel) {
            this.channel = channel;
            return this;
        }

        public UrlShortenRequestBuilder data(UrlShortenData data) {
            this.data = data;
            return this;
        }

        public UrlShortenRequestBuilder data(String path) {
            this.data = UrlShortenData.builder().path(path).build();
            return this;
        }

        public UrlShortenRequestBuilder key(String key) {
            this.key = key;
            return this;
        }

        public UrlShortenRequest build() {
            return new UrlShortenRequest(key, campaign, channel, data);
        }

        public String toString() {
            return "UrlShortenRequest.UrlShortenRequestBuilder(key=" + this.key + ", campaign=" + this.campaign + ", channel=" + this.channel + ", data=" + this.data + ")";
        }
    }
}
