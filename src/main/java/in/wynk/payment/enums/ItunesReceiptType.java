package in.wynk.payment.enums;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.util.Arrays;
import java.util.List;

public enum ItunesReceiptType {
    SIX {

        @Override
        public String getEncodedItunesData(String itunesData) {
            JSONObject jsonObj = null;
            try {
                jsonObj = (JSONObject) JSONValue.parseWithException(itunesData);
            }
            catch (ParseException e) {
                throw new RuntimeException("Error while parsing itunes subscription data " + itunesData);
            }
            String desiredJsonRep = "{";
            for(Object key : jsonObj.keySet()) {
                String param = (String) key;
                String value = (String) jsonObj.get(key);
                desiredJsonRep += (encloseInDoubleQuotes(param) + "=" + encloseInDoubleQuotes(value) + ";");
            }
            String encodedValue = itunesData;//EncryptUtils.encodeBase64(desiredJsonRep + "}", false);
            return encodedValue;
        }

        @Override
        @SuppressWarnings("unchecked")
        public JSONArray getSubscriptionDetailJson(JSONObject receiptFullJsonObj) {
            JSONObject receiptJsonObject = null;
            List<String> keys = Arrays.asList("latest_expired_receipt_info,latest_receipt_info,receipt".split(","));
            for(String key : keys) {
                if(receiptFullJsonObj.get(key) != null) {
                    receiptJsonObject = (JSONObject) receiptFullJsonObj.get(key);
                    break;
                }
            }
            JSONArray arr = new JSONArray();
            arr.add(receiptJsonObject);
            return arr;
        }

        @Override
        public long getPurchaseDate(JSONObject receiptJsonObject) {
            return Long.parseLong((String) receiptJsonObject.get("purchase_date_ms"));
        }

        @Override
        public long getExpireDate(JSONObject receiptJsonObject) {
            try {
                return Long.parseLong((String) receiptJsonObject.get("expires_date_ms"));
            }
            catch (Exception ex) {
                try {
                    return Long.parseLong((String) receiptJsonObject.get("expires_date"));
                }
                catch (Exception e) {
                    return 0L;
                }
            }
        }
    },
    SEVEN {

        @Override
        public String getEncodedItunesData(String itunesData) {
            JSONObject jsonObj = null;
            try {
                jsonObj = (JSONObject) JSONValue.parseWithException(itunesData);
            }
            catch (ParseException e) {
                throw new RuntimeException("Error while parsing itunes subscription data " + itunesData);
            }
            return (String) jsonObj.get("receipt-data");
        }

        @Override
        public JSONArray getSubscriptionDetailJson(JSONObject receiptFullJsonObj) {
            return (JSONArray) receiptFullJsonObj.get("latest_receipt_info");
        }

        @Override
        public long getPurchaseDate(JSONObject receiptJsonObject) {
            return Long.parseLong((String) receiptJsonObject.get("purchase_date_ms"));
        }

        @Override
        public long getExpireDate(JSONObject receiptJsonObject) {
            if(!receiptJsonObject.containsKey("expires_date_ms")) {
                return 0L;
            }
            return Long.parseLong((String) receiptJsonObject.get("expires_date_ms"));
        }
    };

    public static ItunesReceiptType getReceiptType(String payload) {
        if(payload.contains("receipt-data")) {
            return SEVEN;
        }
        if(payload.contains("purchase-info")) {
            return SIX;
        }
        throw new IllegalArgumentException("Illegal value for payload : " + payload);
    }

    public abstract String getEncodedItunesData(String iTunesData);

    public abstract JSONArray getSubscriptionDetailJson(JSONObject receiptFullJsonObj);

    public abstract long getPurchaseDate(JSONObject receiptJsonObject);

    public abstract long getExpireDate(JSONObject receiptJsonObject);

    private static String encloseInDoubleQuotes(String data) {
        char doubleQuotes = '"';
        return doubleQuotes + data + doubleQuotes;
    }
}


