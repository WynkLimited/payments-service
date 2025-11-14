package in.wynk.payment.event;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import in.wynk.common.dto.GeoLocation;
import in.wynk.common.dto.MiscellaneousDetails;
import in.wynk.common.dto.SessionResponse;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.*;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;

import static in.wynk.logging.BaseLoggingMarkers.KAFKA_MESSAGE_CREATOR_ERROR;


@Getter
@Setter
@Builder
@Slf4j
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "purchaseRequest")
public class PurchaseRequestKafkaMessage {
    @JsonUnwrapped
    private AppDetails appDetails;
    @JsonUnwrapped
    private UserDetails userDetails;
    @JsonUnwrapped
    private GeoLocation geoLocation;
    @JsonUnwrapped
    private MiscellaneousDetails miscellaneousDetails;
    @JsonUnwrapped
    private SessionResponse.SessionData data;
    private String planId;

    public static PurchaseRequestKafkaMessage from(PurchaseRequest request, WynkResponseEntity<SessionResponse.SessionData> response) {
        try {
            return PurchaseRequestKafkaMessage.builder()
                    .appDetails(request.getAppDetails() != null ? request.getAppDetails() : AppDetails.builder().build())
                    .geoLocation(request.getGeoLocation() != null ? request.getGeoLocation() : GeoLocation.builder().build())
                    .planId(request.getProductDetails() != null ? request.getProductDetails().getId() : null)
                    .miscellaneousDetails(request.getMiscellaneousDetails() != null ? request.getMiscellaneousDetails() : MiscellaneousDetails.builder().build())
                    .userDetails(request.getUserDetails() != null ? request.getUserDetails() : UserDetails.builder().build())
                    .data(response != null ? Objects.requireNonNull(response.getBody()).getData() : null)
                    .build();
        } catch (Exception ex) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating PurchaseRequestKafkaMessage for response: {}", response, ex);
        }
        return null;
    }
}