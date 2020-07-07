package in.wynk.payment.core.dto.itunes;

import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.apache.commons.codec.binary.Base64;

import java.util.List;

import static in.wynk.payment.core.dto.itunes.ItunesConstant.*;

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
        public List<LatestReceiptInfo> getSubscriptionDetailJson(ItunesReceipt itunesReceipt) {
            if(itunesReceipt.getLatestReceiptInfoList()!=null){
                return itunesReceipt.getLatestReceiptInfoList();
            }
            return null;
        }

        @Override
            public long getPurchaseDate(LatestReceiptInfo latestReceiptInfo) {
                return Long.parseLong(latestReceiptInfo.getPurchaseDateMs());
            }

        @Override
        public long getExpireDate(LatestReceiptInfo latestReceiptInfo) {
            try {
                return Long.parseLong(latestReceiptInfo.getExpiresDateMs());
            }
            catch (Exception ex) {
                try {
                    return Long.parseLong(latestReceiptInfo.getExpiresDate());
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
        public List<LatestReceiptInfo> getSubscriptionDetailJson(ItunesReceipt itunesReceipt) {
            if(itunesReceipt.getLatestReceiptInfoList()!=null){
                return itunesReceipt.getLatestReceiptInfoList();
            }
            return null;
        }

        @Override
        public long getPurchaseDate(LatestReceiptInfo latestReceiptInfo) {
            return Long.parseLong(latestReceiptInfo.getPurchaseDateMs());
        }

        @Override
        public long getExpireDate(LatestReceiptInfo latestReceiptInfo) {
            if(latestReceiptInfo.getExpiresDateMs()==null){
                return 0L;
            }
            return Long.parseLong(latestReceiptInfo.getExpiresDateMs());
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

    public abstract List<LatestReceiptInfo> getSubscriptionDetailJson(ItunesReceipt itunesReceipt);

    public abstract long getPurchaseDate(LatestReceiptInfo latestReceiptInfo);

    public abstract long getExpireDate(LatestReceiptInfo latestReceiptInfo);

    private static String encloseInDoubleQuotes(String data) {
        char doubleQuotes = '"';
        return doubleQuotes + data + doubleQuotes;
    }
}


