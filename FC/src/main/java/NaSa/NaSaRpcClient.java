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

    public GetBlockchainInfo.BlockchainInfo blockchainInfo(String apiUrl, String userName, byte[] password){
        return new GetBlockchainInfo().getBlockchainInfo(apiUrl, userName, new String(password, StandardCharsets.UTF_8));
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
//    public  (){
//        return new .g(,url,username,password);
//    }
}
