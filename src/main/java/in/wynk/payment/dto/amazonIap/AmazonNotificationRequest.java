package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.utils.Utils;
import in.wynk.payment.dto.IAPNotification;
import lombok.Getter;

@Getter
@AnalysedEntity
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AmazonNotificationRequest implements IAPNotification {

    @JsonProperty("Type")
    private String notificationType;
    @JsonProperty("MessageId")
    private String messageId;
    @JsonProperty("Subject")
    private String subject;
    @JsonProperty("TopicArn")
    private String topicArn;
    @JsonProperty("Message")
    private String message;
    @JsonProperty("Timestamp")
    private String timestamp;
    @JsonProperty("SignatureVersion")
    private String signatureVersion;
    @JsonProperty("Signature")
    private String signature;
    @JsonProperty("SigningCertURL")
    private String signingCertURL;
    @JsonProperty("UnsubscribeURL")
    private String unsubscribeURL;
    @JsonProperty("SubscribeURL")
    private String subscribeUrl;
    @JsonProperty("Token")
    private String token;

    @Analysed(name = "message")
    public AmazonNotificationMessage getDecodedMessage() {
        return Utils.getData(message, AmazonNotificationMessage.class);
    }

}
