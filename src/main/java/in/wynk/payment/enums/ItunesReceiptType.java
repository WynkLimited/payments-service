package in.wynk.payment.enums;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import in.wynk.payment.constant.ItunesConstant.*;
import org.apache.commons.codec.binary.Base64;

import static in.wynk.payment.constant.ItunesConstant.*;

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
            return Base64.encodeBase64String((desiredJsonRep + "}").getBytes());
        }

        @Override
        @SuppressWarnings("unchecked")
        public JSONArray getSubscriptionDetailJson(JSONObject receiptFullJsonObj) {
            return (JSONArray) receiptFullJsonObj.get(LATEST_RECEIPT_INFO);
        }

        @Override
        public long getPurchaseDate(JSONObject receiptJsonObject) {
            return Long.parseLong((String) receiptJsonObject.get(PURCHASE_DATE_MS));
        }

        @Override
        public long getExpireDate(JSONObject receiptJsonObject) {
            try {
                return Long.parseLong((String) receiptJsonObject.get(EXPIRES_DATE_MS));
            }
            catch (Exception ex) {
                try {
                    return Long.parseLong((String) receiptJsonObject.get(EXPIRES_DATE));
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
            return (String) jsonObj.get(RECEIPT_DATA);
        }

        @Override
        public JSONArray getSubscriptionDetailJson(JSONObject receiptFullJsonObj) {
            return (JSONArray) receiptFullJsonObj.get(LATEST_RECEIPT_INFO);
        }

        @Override
        public long getPurchaseDate(JSONObject receiptJsonObject) {
            return Long.parseLong((String) receiptJsonObject.get(PURCHASE_DATE_MS));
        }

        @Override
        public long getExpireDate(JSONObject receiptJsonObject) {
            if(!receiptJsonObject.containsKey(EXPIRES_DATE_MS)) {
                return 0L;
            }
            return Long.parseLong((String) receiptJsonObject.get(EXPIRES_DATE_MS));
        }
    };

    public static ItunesReceiptType getReceiptType(String payload) {
        if(payload.contains(RECEIPT_DATA)) {
            return SEVEN;
        }
        if(payload.contains(PURCHASE_INFO)) {
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


