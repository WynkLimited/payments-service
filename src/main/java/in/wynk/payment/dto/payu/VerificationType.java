package in.wynk.payment.dto.payu;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum VerificationType {
    VPA("vpa"),
    BIN("bin");

    private final String type;
}
