package fcData;

import com.google.gson.*;

import java.lang.reflect.Type;

public enum AlgorithmType {
    ECC256k1_AES256CBC("ECC256k1_AES256CBC"),//The earliest one from bitcore which is the same as EccAes256BitPay@No1_NrC7.
    BitPay_EccAes256_No1_NrC7("BitPay-EccAes256@No1_NrC7"),
    EccAes256K1P7_No1_NrC7("EccAes256K1P7@No1_NrC7"),
    FC_EccK1AesCbc256_No1_NrC7("FC-EccK1AesCbc256K1@No1_NrC7"),
    BTC_EcdsaSignMsg_No1_NrC7("BTC-EcdsaSignMsg@No1_NrC7"),
    FC_SchnorrSignTx_No1_NrC7("FC-SchnorrSignTx@No1_NrC7"),
    FC_Aes256Cbc_No1_NrC7("Aes256Cbc@No1_NrC7");

//    private final String name;

//    AlgorithmType(String name) {
//    }
    private final String displayName;

    AlgorithmType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getName() {
        return this.name();
    }
    public static class AlgorithmTypeSerializer implements JsonSerializer<AlgorithmType> {
        @Override
        public JsonElement serialize(AlgorithmType src, Type typeOfSrc, JsonSerializationContext context) {
            return context.serialize(src.getDisplayName());
        }
    }
    public static AlgorithmType fromDisplayName(String displayName) {
        for (AlgorithmType type : AlgorithmType.values()) {
            if (type.getDisplayName().equals(displayName)) {
                return type;
            }
        }
        throw new IllegalArgumentException("Unknown displayName: " + displayName);
    }
    public static class AlgorithmTypeDeserializer implements JsonDeserializer<AlgorithmType> {
        @Override
        public AlgorithmType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return AlgorithmType.fromDisplayName(json.getAsString());
        }
    }

}
