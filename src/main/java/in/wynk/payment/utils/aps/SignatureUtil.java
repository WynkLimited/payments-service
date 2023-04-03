package in.wynk.payment.utils.aps;

import com.fasterxml.jackson.core.JsonProcessingException;

import javax.crypto.BadPaddingException;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.NoSuchPaddingException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Arrays;

public class SignatureUtil {

    public static <T> String generateSignature(T message, String secret, String salt) throws JsonProcessingException, InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException, BadPaddingException, InvalidKeyException {
        String jsonString = JsonConversionUtil.convertToJson(message);
        byte[] messageBytes = jsonString.getBytes(StandardCharsets.UTF_8);
        byte[] generatedHash = generateHash(messageBytes);
        byte[] encryptedBytes = SymmetricalEncryptionUtil.encrypt(generatedHash, secret, salt);
        return EncodingUtil.base64Encode(encryptedBytes);
    }

    public static <T> boolean verifySignature(String encodedEncryptedSignature, T message, String secret, String salt) throws InvalidAlgorithmParameterException, IllegalBlockSizeException, NoSuchPaddingException, NoSuchAlgorithmException, InvalidKeySpecException, BadPaddingException, InvalidKeyException, JsonProcessingException {
        byte[] base64Decode = EncodingUtil.base64Decode(encodedEncryptedSignature);
        byte[] decryptedHash = SymmetricalEncryptionUtil.decrypt(base64Decode, secret, salt);
        String jsonString = JsonConversionUtil.convertToJson(message);
        byte[] messageBytes = jsonString.getBytes(StandardCharsets.UTF_8);
        byte[] generatedHash = generateHash(messageBytes);
        return Arrays.equals(decryptedHash, generatedHash);
    }

    private static byte[] generateHash(byte[] messageBytes) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return md.digest(messageBytes);
    }
}
