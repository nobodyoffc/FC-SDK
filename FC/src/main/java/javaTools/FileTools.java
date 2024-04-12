package javaTools;

import crypto.cryptoTools.Hash;
import org.junit.jupiter.api.Test;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileTools {
    public static File getAvailableFile(BufferedReader br) {
        String input;
        while (true) {
            System.out.println("Input the full path.'s' to skip:");
            try {
                input = br.readLine();
            } catch (IOException e) {
                System.out.println("BufferedReader wrong:" + e.getMessage());
                return null;
            }

            if ("s".equals(input)) return null;

            File file = new File(input);
            if (!file.exists()) {
                System.out.println("\nPath doesn't exist. Input again.");
            } else {
                return file;
            }
        }
    }

    public static File getNewFile(String filePath, String fileName,Mode mode) {
        File file = new File(filePath, fileName);

        int i=1;

        String fileNameHead = getFileNameHead(fileName);
        String fileNameTail = getFileNameTail(fileName,true);

        if(file.exists()){
            switch (mode){
                case ADD_1 -> {
                    while (file.exists()){
                        String newFileName = fileNameHead+"_"+i+fileNameTail;
                        i++;
                        file = new File(filePath,newFileName);
                    }
                }
                case REWRITE -> System.out.println("File "+file.getName()+" existed. It will be covered.");
                case RETURN_NULL -> {
                    System.out.println("File "+file.getName()+" existed.");
                    return null;
                }
                case THROW_EXCEPTION -> throw new RuntimeException("File "+file.getName()+" existed.");
            }
        }
        try {
            if (file.createNewFile()) {
                System.out.println("File "+file.getName()+" created.");
                return file;
            } else {
                System.out.println("Create new file " + fileName + " failed.");
                return null;
            }
        } catch (IOException e) {
            System.out.println("Create new file " + fileName + " wrong:" + e.getMessage());
            return null;
        }
    }

    public static boolean writeBytesToDidFile(byte[] bytes, String storageDir) {
        String did = Hex.toHex(Hash.Sha256x2(bytes));
        String subDir = getSubDirPathOfDid(did);
        String path = storageDir+subDir;

        File file = new File(path,did);
        if(!file.exists()) {
            try {
                boolean done = createFileWithDirectories(path+"/"+did);
                if(!done)return false;
                try (OutputStream outputStream = new FileOutputStream(file)) {
                    outputStream.write(bytes);
                    return true;
                }
            } catch (IOException e) {
                return false;
            }
        }else return checkFileExistsWithDid(bytes, storageDir, did);
    }

    public static boolean createFileWithDirectories(String filePathString) {
        Path path = Paths.get(filePathString);
        try {
            // Create parent directories if they do not exist
            if (Files.notExists(path.getParent())) {
                Files.createDirectories(path.getParent());
            }

            // Create file if it does not exist
            if (Files.notExists(path)) {
                Files.createFile(path);
                return true;
            } else {
                return true;
            }
        } catch (IOException e) {
            System.err.println("Error creating file or directories: " + e.getMessage());
            return false;
        }
    }

    public static String getSubDirPathOfDid(String did) {
        return "/"+did.substring(0,2)+"/"+did.substring(2,4)+"/"+did.substring(4,6)+"/"+did.substring(6,8);
    }

    public static boolean checkFileExistsWithDid(byte[] bytes, String storageDir, String did) {
        if (bytes==null)return false;
        File file = new File(storageDir,did);
        if(!file.exists())return false;
        String inputDid = Hex.toHex(Hash.Sha256x2(bytes));
        byte[] existBytes;
        try(FileInputStream fileInputStream = new FileInputStream(file)) {
            existBytes = fileInputStream.readAllBytes();
        } catch (IOException e) {
            return false;
        }
        String existDid = Hex.toHex(Hash.Sha256x2(existBytes));
        return inputDid.equals(existDid);
    }

    public static boolean checkFileExistsWithDid(String storageDir, String did) {
        String path = FileTools.getSubDirPathOfDid(did);
        File file = new File(storageDir+path, did);
        if(!file.exists())return false;
        byte[] existBytes;
        try(FileInputStream fileInputStream = new FileInputStream(file)) {
            existBytes = fileInputStream.readAllBytes();
        } catch (IOException e) {
            return false;
        }
        String existDid = Hex.toHex(Hash.Sha256x2(existBytes));
        return did.equals(existDid);
    }
    public static enum Mode{
        REWRITE,ADD_1,RETURN_NULL,THROW_EXCEPTION
    }

    @Test
    public void test(){
        String name = "a.b.txt";
        System.out.println(getFileNameTail(name,false));
        getNewFile(null,"config.json",Mode.REWRITE);
    }
    private static String getFileNameHead(String fileName) {
        int dotIndex = fileName.lastIndexOf(".");
        return fileName.substring(0,dotIndex);
    }
    private static String getFileNameTail(String fileName,boolean withDot) {
        int dotIndex = fileName.lastIndexOf(".");
        if(!withDot)dotIndex+=1;
        return fileName.substring(dotIndex);
    }
}
