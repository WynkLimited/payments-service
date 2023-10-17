package in.wynk.payment.dto.aps.common;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
public class ChannelInfo {
    private ChannelMeta channelMeta;

    @Getter
    @SuperBuilder
    @ToString
    @NoArgsConstructor
    public static class ChannelMeta {
        private String text;
    }
}
