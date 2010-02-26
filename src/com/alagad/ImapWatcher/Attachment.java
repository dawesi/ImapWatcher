package com.alagad.ImapWatcher;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import coldfusion.runtime.RuntimeServiceImpl.FileNotFoundException;

public class Attachment {

	private String contentType;
	private String fileName;
	private byte[] content;
	
	public Attachment(String contentType, String fileName, InputStream contentStream) throws IOException{
		
		setContentType(contentType);
		setFileName(fileName);
		
		ByteArrayOutputStream bos=new ByteArrayOutputStream();
        
        byte[] buffer=new byte[8192];
        int count=0;
        
        while((count=contentStream.read(buffer))>=0){
        	bos.write(buffer,0,count);
        }
        contentStream.close();
        
        setContent(bos.toByteArray());
	}

	public void writeToDisk(String directory) throws FileNotFoundException, IOException{
		writeToDisk(directory, getFileName());
	}
	
	public void writeToDisk(String directory, String name) throws FileNotFoundException, IOException{
		FileOutputStream fos = new FileOutputStream(directory + "/" + name);
		fos.write(getContent());
		fos.close();
	}
	
	public String getContentType() {
		return contentType;
	}

	private void setContentType(String contentType) {
		this.contentType = contentType;
	}

	public String getFileName() {
		return fileName;
	}

	private void setFileName(String fileName) {
		this.fileName = fileName;
	}

	public byte[] getContent() {
		return content;
	}

	private void setContent(byte[] content) {
		this.content = content;
	}
	
	
	
}
