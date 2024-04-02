package server.reward;

import fcTools.DataForOffLineTx;
import fchClass.Cash;

import java.util.List;

public class Payroll {

    RewardInfo rewardInfo;
    String account;
    DataForOffLineTx dataForOffLineTx;
    List<Cash> meetCashList;

    public Payroll(String account,RewardInfo rewardInfo) {
        this.rewardInfo = rewardInfo;
        this.account = account;
    }

    public String makePayrollJson(){

        return null;
    }


}
