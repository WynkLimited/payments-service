package in.wynk.payment.utils.aps;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.TreeMap;

public class JsonConversionUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public JsonConversionUtil() {
    }

    public static ObjectMapper getObjectMapper() {
        return objectMapper;
    }

    public static <T> String convertToJson(T object) throws JsonProcessingException {
        return objectMapper.writeValueAsString(convertObjectSilently(object, new TypeReference<TreeMap>() {
        }));
    }

    public static <T> T convertFromJson(String jsonString, Class<T> clazz) throws JsonProcessingException {
        return objectMapper.readValue(jsonString, clazz);
    }

    public static <T> T convertFromJson(String jsonString, TypeReference<T> typeReference) throws JsonProcessingException {
        return objectMapper.readValue(jsonString, typeReference);
    }

    public static <S, R> R convertObject(S source, TypeReference<R> typeReference) throws IllegalArgumentException {
        return objectMapper.convertValue(source, typeReference);
    }

    public static <S, R> R convertObjectSilently(S source, TypeReference<R> typeReference) {
        return objectMapper.convertValue(source, typeReference);
    }

    static {
        objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
        objectMapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    }
}
