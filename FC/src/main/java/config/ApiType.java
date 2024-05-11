package config;

public enum ApiType {
    NASARPC,
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
