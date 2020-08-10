package in.wynk.payment.dto.payu;


import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@AnalysedEntity
public enum VerificationType {
    VPA("vpa"),
    BIN("bin");
    @Analysed
    private final String type;
}
