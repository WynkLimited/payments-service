package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AmazonNotificationRequest {

    @JsonProperty("Type")
    public String type;
    @JsonProperty("MessageId")
    public String messageId;
    @JsonProperty("Subject")
    public String subject;
    @JsonProperty("TopicArn")
    public String topicArn;
    @JsonProperty("Message")
    public String message;
    @JsonProperty("Timestamp")
    public String timestamp;
    @JsonProperty("SignatureVersion")
    public String signatureVersion;
    @JsonProperty("Signature")
    public String signature;
    @JsonProperty("SigningCertURL")
    public String signingCertURL;
    @JsonProperty("UnsubscribeURL")
    public String unsubscribeURL;
    @JsonProperty("SubscribeURL")
    private String subscribeUrl;
    @JsonProperty("Token")
    private String token;

}
