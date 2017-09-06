package core;

/**
 * Created by ustc on 2016/6/23.
 */
public class myFile {

    public  String fileName;
    private int  fileSize;


    public int getFileSize() {
        return fileSize;
    }

    public myFile(DTNHost  host){

        this.fileName=host.getAddress()+"intersting_file";
        this.fileSize= 1000;


    }



}
