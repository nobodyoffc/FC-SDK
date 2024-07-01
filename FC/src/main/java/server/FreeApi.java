package server;

import config.ApiType;

public class FreeApi {
    private String urlHead;
    private Boolean active;
    private String sid;
    private ApiType apiType;

    public FreeApi() {
    }

    public FreeApi(String urlHead,Boolean active) {
        this.active = active;
        this.urlHead = urlHead;
    }

    public String getUrlHead() {
        return urlHead;
    }

    public void setUrlHead(String urlHead) {
        this.urlHead = urlHead;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getSid() {
        return sid;
    }

    public void setSid(String sid) {
        this.sid = sid;
    }

    public ApiType getApiType() {
        return apiType;
    }

    public void setApiType(ApiType apiType) {
        this.apiType = apiType;
    }
}
