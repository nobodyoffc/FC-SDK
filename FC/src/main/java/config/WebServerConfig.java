package config;

public class WebServerConfig {
    private String sid;
    private String configPath;
    private String settingPath;
    private String dataPath;

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public String getSettingPath() {
        return settingPath;
    }

    public void setSettingPath(String settingPath) {
        this.settingPath = settingPath;
    }

    public String getDataPath() {
        return dataPath;
    }

    public void setDataPath(String dataPath) {
        this.dataPath = dataPath;
    }
}
