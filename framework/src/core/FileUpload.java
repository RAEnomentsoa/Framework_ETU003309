package core;

public class FileUpload {
    private String fileName;
    private byte[] bytes;
    private String contentType;

    public FileUpload() {
    }

    public FileUpload(String fileName, byte[] bytes, String contentType) {
        this.fileName = fileName;
        this.bytes = bytes;
        this.contentType = contentType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public byte[] getBytes() {
        return bytes;
    }

    public void setBytes(byte[] bytes) {
        this.bytes = bytes;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }
}
