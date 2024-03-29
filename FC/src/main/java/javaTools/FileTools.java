package javaTools;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
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
