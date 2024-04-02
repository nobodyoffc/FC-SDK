package FCH;

import crypto.cryptoTools.KeyTools;
import org.bitcoinj.core.ECKey;
import org.jetbrains.annotations.Nullable;
import crypto.cryptoTools.Base58;


import java.io.BufferedReader;
import java.io.IOException;
import java.util.ArrayList;

public class Inputer extends appTools.Inputer {
    public static String inputGoodFid(BufferedReader br, String ask) {

        String fid;
        while (true) {
            System.out.println(ask);
            fid = inputString(br);
            if (fid == null) return null;
            if ("".equals(fid)) return "";
            if ("d".equals(fid)) return "d";
            if (!KeyTools.isValidFchAddr(fid)) {
                System.out.println("It's not a valid FID. Try again.");
                continue;
            }
            return fid;
        }
    }

    public static String[] inputFidArray(BufferedReader br, String ask, int len) {
        ArrayList<String> itemList = new ArrayList<String>();
        System.out.println(ask);
        while (true) {
            String item = appTools.Inputer.inputString(br);
            if (item.equals("")) break;
            if (!KeyTools.isValidFchAddr(item)) {
                System.out.println("Invalid FID. Try again.");
                continue;
            }
            if (item.startsWith("3")) {
                System.out.println("Multi-sign FID can not used to make new multi-sign FID. Try again.");
                continue;
            }
            if (len > 0) {
                if (item.length() != len) {
                    System.out.println("The length does not match.");
                    continue;
                }
            }
            itemList.add(item);
            System.out.println("Input next item if you want or enter to end:");
        }
        if (itemList.isEmpty()) return new String[0];

        String[] items = itemList.toArray(new String[itemList.size()]);

        return items;
    }

    public static char[] inputPriKeyWif(BufferedReader br) {
        char[] priKey = new char[52];
        int num = 0;
        try {
            num = br.read(priKey);

            if (num != 52 || !Base58.isBase58Encoded(priKey)) {
                System.out.println("The key should be 52 characters and Base58 encoded.");
                return null;
            }
            br.read();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return priKey;
    }


    @Nullable
    public static ECKey inputPriKey(BufferedReader br) {
        byte[] priKey32;
        String input = inputString(br, "Generate a new private key? y/n");
        if ("y".equals(input)) {
            return KeyTools.genNewFid(br);
        } else {
            priKey32 = KeyTools.inputCipherGetPriKey(br);
            if (priKey32 != null) return ECKey.fromPrivate(priKey32);
        }
        return null;
    }
}