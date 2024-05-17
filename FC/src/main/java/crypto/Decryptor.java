package crypto;

public class Decryptor {
    private CryptoData cryptoData;
    private CryptoDataByte cryptoDataByte;

    public Decryptor(CryptoData cryptoData) {
        this.cryptoData = cryptoData;
    }

//    public void decrypt(CryptoDataByte cryptoDataByte) {
//        switch (cryptoDataByte.getType()) {
//            case AsyOneWay -> decryptAsy(cryptoDataByte);
//            case AsyTwoWay -> decryptAsy(cryptoDataByte);
//            case SymKey -> decryptWithSymKey(cryptoDataByte);
//            case Password -> decryptWithPassword(cryptoDataByte);
//        }
//    }
}
