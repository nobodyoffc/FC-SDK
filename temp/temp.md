

public Map<String, Team> teamByIds(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
Object data = requestByIds(httpRequestMethod,SN_8,Version2, TeamByIds, authType, ids);
return CollectionTools.objectToMap(data,String.class,Team.class);
}
public List<Team> teamSearch(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
Object data = requestJsonByFcdsl(SN_8, Version2, TeamSearch, fcdsl, authType, sessionKey, httpRequestMethod);
if(data==null)return null;
return objectToList(data,Team.class);
}

    public List<TeamHistory> teamOpHistory(Fcdsl fcdsl, HttpRequestMethod httpRequestMethod, AuthType authType){
        Object data = requestJsonByFcdsl(SN_8, Version2, TeamOpHistory, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,TeamHistory.class);
    }
    public Map<String, String[]> teamMembers(HttpRequestMethod httpRequestMethod, AuthType authType, String... ids){
        Object data = requestByIds(httpRequestMethod,SN_8,Version2, TeamMembers, authType, ids);
        return CollectionTools.objectToMap(data,String.class,String[].class);
    }

    public List<MyTeamData> myTeams(String fid,HttpRequestMethod httpRequestMethod, AuthType authType){
        Fcdsl fcdsl = new Fcdsl();
        fcdsl.addNewQuery().addNewTerms().addNewFields(FieldNames.MEMBERS).addNewValues(fid);
        Object data = requestJsonByFcdsl(SN_8, Version2, MyTeams, fcdsl, authType, sessionKey, httpRequestMethod);
        if(data==null)return null;
        return objectToList(data,MyTeamData.class);
    }