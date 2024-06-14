package config;

public enum ApiType {
    NASA_RPC,
    APIP,
    ES,
    REDIS,
    DISK,
    SWAP,
    OTHER;

    @Override
    public String toString() {
        return this.name();
    }
}
