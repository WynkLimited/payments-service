package in.wynk.payment.dto.aps.request.order;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@AnalysedEntity
public class ChannelInfo {
    private ChannelMeta channelMeta;

    @Getter
    @Builder
    public static class ChannelMeta {
        private String text;
    }
}
