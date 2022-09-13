package com.chat;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

public class FileTransmission {

    ArrayList<String> filePaths;
    FileTransmission(){
        filePaths=new ArrayList<>();
    }

    synchronized void FileUpload(String FilePath,Socket targetSocket) throws IOException{
        File file=new File(FilePath);
        if(!(file.exists())){
            System.out.println("The designated file doesn't exist.");
            return;
        }else if(!(file.isFile())){
            System.out.println("The file is not qualified.");
            return;
        }
        else if(!(file.canRead())){
            System.out.println("The file cannot be read.");
            return;
        }
        this.filePaths.add(FilePath);
        BufferedOutputStream outputStream=new BufferedOutputStream(targetSocket.getOutputStream());
        outputStream.write((FilePath).getBytes(StandardCharsets.UTF_8));
        outputStream.close();
        System.out.println("File uploaded.");
    }

    void FileDownload(String FilePath,Socket targetSocket) throws Exception{
        BufferedOutputStream outputStream=new BufferedOutputStream(targetSocket.getOutputStream());
        FileInputStream inputStream=new FileInputStream(new File(FilePath));

        // TODO: use inputStream to read and outputStream to write

        inputStream.close();
        outputStream.close();
    }


}

