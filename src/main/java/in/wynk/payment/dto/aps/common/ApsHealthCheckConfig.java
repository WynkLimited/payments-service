package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ApsHealthCheckConfig {
    private String title;
    private String iconUrl;
    private String titleColor;
    private boolean bankEnable;

    private PopUP popUp;

    @Getter
    @ToString
    private static class PopUP {
        private String iconUrl;
        private String subTitle;
        private String description;
        private String subTitleColor;
    }
}