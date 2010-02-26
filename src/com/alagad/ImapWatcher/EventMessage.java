package com.alagad.ImapWatcher;

import java.util.ArrayList;

import javax.mail.Address;
import javax.mail.Message;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Message.RecipientType;
import javax.mail.internet.InternetAddress;

import coldfusion.eventgateway.Logger;

public class EventMessage {
	
	private boolean loggingEnabled = true;
	private Logger logger = null;

	private String subject = "";
	private String text = "";
	private String html = "";
	private String from = "";
	private ArrayList<String> to = new ArrayList<String>();
	private ArrayList<String> cc = new ArrayList<String>();
	private ArrayList<Attachment> attachments = new ArrayList<Attachment>();
	
	public EventMessage(Message message, boolean loggingEnabled, Logger logger) throws Exception{
		this.loggingEnabled = loggingEnabled;
		this.logger = logger;
		
		setSubject(message.getSubject());
		log("get from");
		setFrom(((InternetAddress)message.getFrom()[0]).getAddress());
		
		log("get to");
		int x;
		Address to[] = message.getRecipients(RecipientType.TO);
		if(to != null){
			for(x=0 ; x < to.length ; x++){
				this.to.add(((InternetAddress)to[x]).getAddress());
			}
		}
		
		log("get cc");
		Address cc[] = message.getRecipients(RecipientType.CC);
		if(cc != null){
			for(x=0 ; x < cc.length ; x++){
				this.cc.add(((InternetAddress)cc[x]).getAddress());
			}
		}
		
		// parse this message for different parts
		log("parse this message for different parts");
		parseMessage(message);
		log("got here");
	}
	
	public String toString(){
		return getSubject();
	}
	
	public String getToList(){
		StringBuffer list = new StringBuffer();
		
		for(String address:this.to){
			list.append(this.to + ",");
		}
		
		return list.toString();
	}
	
	public String getCCList(){
		StringBuffer list = new StringBuffer();
		
		for(String address:this.cc){
			list.append(this.cc + ",");
		}
		
		return list.toString();
	}
	
	private void parseMessage(Part part) throws Exception{
		parseMessage(part, 0);
	}
	
	private void parseMessage(Part part, int level) throws Exception{
		if(part.getContent() instanceof Multipart) {
			Multipart mp=(Multipart)part.getContent();

			log("this part has # parts: " + mp.getCount() );
			log("this part's type: " + mp.getContentType() );
			
			for(int x = 0 ; x < mp.getCount() ; x++){
				parseMessage(mp.getBodyPart(x), level+1);
			}
			
		} else {
			String disp = part.getDisposition();
			log("this part's disposition: " + disp);
			
			// figure out if this part is an attachment or not
			boolean attachment = false;
			if(disp != null && disp.equalsIgnoreCase(Part.ATTACHMENT)){
				attachment = true;
			}
			
			if(!attachment && part.getContentType().toLowerCase().startsWith("text/plain")){
				this.setText(part.getContent().toString());
			} else if(!attachment && part.getContentType().toLowerCase().startsWith("text/html")){
				this.setHtml(part.getContent().toString());
			} else if(attachment) {
				attachments.add(new Attachment( part.getContentType(), part.getFileName(), part.getInputStream() ));
			} else {
				log("some content that's not text,html, or an attachment. what is it?!");
			}
			
		}
	}
	
	private void log(String message){
		if(this.loggingEnabled){
			logger.info("EventMessage: " + message);
		}
	}

	private void setSubject(String subject) {
		this.subject = subject;
	}

	public String getSubject() {
		return subject;
	}

	private void setText(String text) {
		this.text = text;
	}

	public String getText() {
		return text;
	}

	private void setHtml(String html) {
		this.html = html;
	}

	public String getHtml() {
		return html;
	}

	private void setFrom(String from) {
		this.from = from;
	}

	public String getFrom() {
		return from;
	}
	
	public ArrayList<String> getTo() {
		return to;
	}
	public ArrayList<String> getCc() {
		return cc;
	}
	public ArrayList<Attachment> getAttachments() {
		return attachments;
	}
	
}
