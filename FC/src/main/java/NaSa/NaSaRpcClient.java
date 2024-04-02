package NaSa;

import NaSa.RPC.*;
import NaSa.data.TransactionRPC;
import NaSa.data.Utxo;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.StandardCharsets;

public class NaSaRpcClient {
    String url;
    String username;
    String password;
    String bestBlockId;
    long bestHeight;

    public NaSaRpcClient(String url, String username, String password) {
        this.url = url;
        this.username = username;
        this.password = password;
    }

    public NaSaRpcClient(String url, String username, byte[] password) {
        this.url = url;
        this.username = username;
        this.password = new String(password,StandardCharsets.UTF_8);
    }

    public boolean freshBestBlock(){
        GetBlockchainInfo.BlockchainInfo blockchainInfo = getBlockchainInfo();
        if(blockchainInfo==null) return false;
        this.bestHeight = blockchainInfo.getBlocks()-1;
        this.bestBlockId = blockchainInfo.getBestblockhash();
        return true;
    }

    public String createRawTransactionFch(String toAddr, double amount, String opreturn) {
        return new CreateRawTransaction().createRawTransactionFch(toAddr,amount,opreturn,url,username,password);
    }

    public String createRawTransactionDoge(String toAddr, double amount, String opreturn) {
        return new CreateRawTransaction().createRawTransactionDoge(toAddr,amount,opreturn,url,username,password);
    }

    public double estimateFee(String url, String username, String password){
        return new EstimateFee().estimatefee(url,username,password);
    }

    public double estimateFee(int nBlocks){
        return new EstimateFee().estimatefee(nBlocks,url,username,password);
    }

    public EstimateFee.ResultSmart estimateSmartFee(int nBlocks){
        return new EstimateFee().estimatesmartfee(nBlocks,url,username,password);
    }

    public FundRawTransaction.FundRawTransactionResult fundRawTransaction(String changeAddr, String rawTxHex, boolean includeWatchOnly, boolean receiverPayFee) {
        return new FundRawTransaction().fundRawTransaction(changeAddr,rawTxHex,includeWatchOnly,receiverPayFee,url,username,password);
    }

    public FundRawTransaction.FundRawTransactionResult fundRawTransaction(String rawTxHex) {
        return new FundRawTransaction().fundRawTransaction(rawTxHex,url,username,password);
    }

    public double balance(String minConf, boolean includeWatchOnly){
        return new GetBalance().getBalance(minConf,includeWatchOnly,url,username,password);
    }

    public GetBlockchainInfo.BlockchainInfo getBlockchainInfo(){
        return new GetBlockchainInfo().getBlockchainInfo(url, username, password);
    }

    public String blockHash(long height){
        return new GetBlockHash().getBlockHash(height,url,username,password);
    }

    public GetBlockHeader.BlockHeader blockHeader(String blockId){
        return new GetBlockHeader().getBlockHeader(blockId,url,username,password);
    }

    public String getRawTx(String txId){
        return new GetRawTx().getRawTx(txId,url,username,password);
    }

    public TransactionRPC getTransaction(String txId, boolean includeWatchOnly){
        return new GetTrasaction().getTransaction(txId,includeWatchOnly,url,username,password);
    }
    public ListSinceBlock.ListSinceBlockResult listSinceBlock(String block, String minConf, boolean includeWatchOnly){
        return new ListSinceBlock().listSinceBlock(block,minConf,includeWatchOnly,url,username,password);
    }

    public Utxo[] listUnspent(){
        return new ListUnspent().listUnspent(url,username,password);
    }

    public Utxo[] listUnspent(@Nullable String addr, @Nullable String minConf){
        return new ListUnspent().listUnspent(addr,minConf,url,username,password);
    }

    public Utxo[] listUnspent(@Nullable String addr, @Nullable String minConf,boolean includeUnsafe){
        return new ListUnspent().listUnspent(addr,minConf,includeUnsafe,url,username,password);
    }

    public String sendRawTransaction(String hex){
        return new SendRawTransaction().sendRawTransaction(hex,url,username,password);
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getBestBlockId() {
        return bestBlockId;
    }

    public void setBestBlockId(String bestBlockId) {
        this.bestBlockId = bestBlockId;
    }

    public long getBestHeight() {
        return bestHeight;
    }

    public void setBestHeight(long bestHeight) {
        this.bestHeight = bestHeight;
    }
    //    public  (){
//        return new .g(,url,username,password);
//    }
}
