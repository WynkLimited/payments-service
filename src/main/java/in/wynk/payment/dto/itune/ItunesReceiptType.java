package in.wynk.payment.dto.itune;

import in.wynk.exception.WynkRuntimeException;
import org.apache.commons.codec.binary.Base64;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;

import java.util.List;

public enum ItunesReceiptType {
    SIX {

        @Override
        public String getEncodedItunesData(String itunesData) {
            JSONObject jsonObj;
            try {
                jsonObj = (JSONObject) JSONValue.parseWithException(itunesData);
            }
            catch (ParseException e) {
                throw new WynkRuntimeException("Error while parsing itunes subscription data " + itunesData);
            }
            StringBuilder desiredJsonRep = new StringBuilder("{");
            for (Object key : jsonObj.keySet()) {
                String param = (String) key;
                String value = (String) jsonObj.get(key);
                desiredJsonRep.append(encloseInDoubleQuotes(param)).append("=").append(encloseInDoubleQuotes(value)).append(";");
            }
            return Base64.encodeBase64String((desiredJsonRep + "}").getBytes());
        }

        @Override
        public List<LatestReceiptInfo> getSubscriptionDetailJson(ItunesReceipt itunesReceipt) {
            if (itunesReceipt.getLatestReceiptInfoList() != null) {
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

        @Override
        public long getCancellationDate(LatestReceiptInfo latestReceiptInfo) {
            if(latestReceiptInfo.getCancellationDateMs()==null){
                return Long.MAX_VALUE;
            }
            return Long.parseLong(latestReceiptInfo.getCancellationDateMs());
        }

        @Override
        public <T> T getOrDefault(String receipt, String key, T defaultValue) {
            return defaultValue;
        }
    },
    SEVEN {
        @Override
        public String getEncodedItunesData(String itunesData) {
            JSONObject jsonObj;
            try {
                jsonObj = (JSONObject) JSONValue.parseWithException(itunesData);
            } catch (ParseException e) {
                throw new WynkRuntimeException("Error while parsing itunes subscription data " + itunesData);
            }
            return (String) jsonObj.get(ItunesConstant.RECEIPT_DATA);
        }

        @Override
        public List<LatestReceiptInfo> getSubscriptionDetailJson(ItunesReceipt itunesReceipt) {
            if (itunesReceipt.getLatestReceiptInfoList() != null) {
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

        @Override
        public long getCancellationDate(LatestReceiptInfo latestReceiptInfo) {
            if(latestReceiptInfo.getCancellationDateMs()==null){
                return Long.MAX_VALUE;
            }
            return Long.parseLong(latestReceiptInfo.getCancellationDateMs());
        }

        @Override
        public <T> T getOrDefault(String receipt, String key, T defaultValue) {
            try {
                JSONObject obj = (JSONObject) JSONValue.parseWithException(receipt);
                return (T) obj.getOrDefault(key, defaultValue);
            } catch (ParseException e) {
                throw new WynkRuntimeException("Error while parsing itunes subscription data " + receipt);
            }
        }
    };

    public static ItunesReceiptType getReceiptType(String payload) {
        if (payload.contains(ItunesConstant.RECEIPT_DATA)) {
            return SEVEN;
        }
        if (payload.contains(ItunesConstant.PURCHASE_INFO)) {
            return SIX;
        }
        throw new IllegalArgumentException("Illegal value for payload : " + payload);
    }

    public String getTransactionId(LatestReceiptInfo receiptInfo) {
        return receiptInfo.getTransactionId();
    }

    public long getOriginalTransactionId(LatestReceiptInfo receiptInfo) {
        return Long.parseLong(receiptInfo.getOriginalTransactionId());
    }

    public abstract String getEncodedItunesData(String iTunesData);

    public abstract List<LatestReceiptInfo> getSubscriptionDetailJson(ItunesReceipt itunesReceipt);

    public abstract long getPurchaseDate(LatestReceiptInfo latestReceiptInfo);

    public abstract long getExpireDate(LatestReceiptInfo latestReceiptInfo);

    public abstract long getCancellationDate(LatestReceiptInfo latestReceiptInfo);

    private static String encloseInDoubleQuotes(String data) {
        char doubleQuotes = '"';
        return doubleQuotes + data + doubleQuotes;
    }
    public abstract <T> T getOrDefault(String receipt, String key, T defaultValue);
}


