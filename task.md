[] 测试swap client
[] APIP SignInEcc change to (apiAccount,symKey)
[] test apip signIn,disk signIN

[] 所有接口：get 1个，post 多个
[] 增加SignIn, LIST
[] 增加fcdsl的查询，
[] 存储结构：256*4，
* api需求：
  * fch数据：读 apip，nasa（指定地址数据）
  * feip：读 apip
  * 本服务数据：存 redis，file，es，apip（swap）
* 服务类型
  * 全自营：nasa+es+redis，FCH-FEIP-APIP
  * 全接口：apip+redis+file

[] OpenDrive
[] documents
    [] FEIP
    [] APIP
[] Client
    [] Freeverse
    [] FreeBuilder: nid, protocol, code, FEIP builder, APIP Builder
[] APPs
    [] freeSign3
    [] 

* 输入密文，解密，重新加密保存，返回明文字节
* 解密symKey加密密文
* 