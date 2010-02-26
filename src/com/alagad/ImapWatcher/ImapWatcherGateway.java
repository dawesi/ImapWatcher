package com.alagad.ImapWatcher;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.mail.AuthenticationFailedException;
import javax.mail.Flags;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.event.MessageCountEvent;
import javax.mail.event.MessageCountListener;
import javax.mail.search.FlagTerm;

import com.sun.mail.imap.IMAPFolder;

import coldfusion.eventgateway.CFEvent;
import coldfusion.eventgateway.Gateway;
import coldfusion.eventgateway.GatewayHelper;
import coldfusion.eventgateway.GatewayServices;
import coldfusion.eventgateway.Logger;
import coldfusion.server.ServiceRuntimeException;

public class ImapWatcherGateway implements Gateway, MessageCountListener {

	private String id = "";
	private int status = Gateway.STOPPED;
	private Logger logger = null;
	private boolean loggingEnabled = true;
	private GatewayServices gatewayServices = null;
	private String[] cfcListeners = null;
	private Properties properties = new Properties();
	private Session session = null;
	private Store store = null;
	private IMAPFolder folder = null;
	private IMAPFolder targetFolder = null;
	private String regex = "";
	private String functionToCall = "";
	
	private Thread listenerThread;
	private boolean shutdown = false;
    
	public ImapWatcherGateway(String id, String configFile){
		this.id = id;
		
		// get the logger from gateway services
		this.gatewayServices = GatewayServices.getGatewayServices();
		this.logger = this.gatewayServices.getLogger();
		
		// load the configuration
		log("Loading Configuration File (constructor)");
		
        try {
            FileInputStream propsFile = new FileInputStream(configFile);
            this.properties.load(propsFile);
            propsFile.close();
        } catch (IOException e) {
            String error = this.id +": Unable to load configuration file: " + configFile;
            throw new ServiceRuntimeException(error, e);
        }
	
	}
	
	@Override
	public String getGatewayID() {
		return this.id;
	}

	@Override
	public GatewayHelper getHelper() {
		return null;
	}

	@Override
	public int getStatus() {
		// TODO Auto-generated method stub
		return this.status;
	}

	@Override
	public String outgoingMessage(CFEvent event) {
		// TODO Auto-generated method stub
		return "ERROR: outgoingMessage not supported";
	}

	@Override
	public void restart() {
        this.stop();
        this.start();
	}

	@Override
	public void setCFCListeners(String[] listeners) {
		this.cfcListeners = listeners;
	}

	@Override
	public void setGatewayID(String id) {
		this.id = id;
	}

	@Override
	public void start() {
		this.status = Gateway.STARTING;
		
		this.loggingEnabled = Boolean.parseBoolean(properties.getProperty("loggingEnabled"));
		
		log("Starting Gateway");
		
		 // Start up listener thread
        Runnable r = new Runnable()
        {
            public void run()
            {
                listener();
            }
        };
        listenerThread = new Thread(r);
		this.shutdown = false;
        listenerThread.start();
		
		this.status = Gateway.RUNNING;
		
	}
	
	private void listener(){
		try{
			
			log("Connecting to IMAP server: " + this.properties.getProperty("hostname"));
			this.session = Session.getDefaultInstance(this.properties);
			this.store = this.session.getStore("imaps");
			this.store.connect(this.properties.getProperty("hostname"), this.properties.getProperty("username"), this.properties.getProperty("password"));
			
			this.regex = this.properties.getProperty("regex", "");
			this.functionToCall = this.properties.getProperty("functionToCall");
			
			dumpFolder((IMAPFolder)this.store.getDefaultFolder(), 0);
			
			log("Opening folder: " + this.properties.getProperty("folder"));
			this.folder = (IMAPFolder)store.getFolder(this.properties.getProperty("folder"));
			this.folder.open(IMAPFolder.READ_WRITE);
			
			if(this.properties.getProperty("postEventAction").equals("move")){
				this.targetFolder = (IMAPFolder)store.getFolder(this.properties.getProperty("targetFolder"));
			}
			
			announceMessages();
		
			// add the watcher
			this.folder.addMessageCountListener(this);
			
			while(!shutdown) {
				//log("start idle.");
				this.folder.idle();
				//log("done idle.");
			}
			
			log("IMAP Watcher shutting down.", true);
			
		} catch(AuthenticationFailedException e) {	
			log("AuthenticationFailedException: " + e.toString(), true);
			stop();
		} catch(Exception e){
			// this is a bit of a hack.  For now, if a connection drops I have this setup to simply 
			// stop and restart the gateway.
			log(e.toString(), true);
			restart();
		}
	}
	
	private void dumpFolder(Folder folder, int level){
		log(getDashes(level) + folder.getName() + "(" + folder.getFullName() + ")");
		
		try{
			Folder folders[] = folder.list();
			for(Folder thisFolder:folders){
				dumpFolder(thisFolder, level+1);				
			}
		} catch(Exception e){
			log("Folder Exception: " + e.toString(), true);
		}
		
	}
	private String getDashes(int count){
		String dashes = "";
		for(int x = 0 ; x < count ; x++){
			dashes += "-";
		}
		return dashes;
	}
	
	private void announceMessages(){
		try{
			// based on the search options, search the folder
			Message messages[] = null;
			if(this.properties.getProperty("searchOption").equals("unread")){
				messages = this.folder.search(new FlagTerm(new Flags(Flags.Flag.SEEN), false));				
			} else {
				messages = this.folder.getMessages();
			}
			
			for(Message message:messages) {
				EventMessage eventMessage = new EventMessage(message, loggingEnabled, logger);

				/*log("text: " + eventMessage.getText());
				log("html: " + eventMessage.getHtml());
				log("subject: " + eventMessage.getSubject());
				log("attachments: " + eventMessage.getAttachments().size());*/
				
				// TODO all this crap down here needs to change
				
				// let's start out by assuming this message can be used.
				boolean found = true;
				
				// check to see if we're filtering based on a regular expression
				if(!this.regex.equals("")){
					// because we're filtering on regex we need to assumet the message is actually NOT found yet.
					found = false;
					
					// we're using a regex!
					log("Need to match message against regex: " + this.regex);
					
					// check the subject and body (text or html) for a match to the pattern
					Pattern p = Pattern.compile(this.regex);
					Matcher subjectMatcher = p.matcher(eventMessage.getSubject());
					Matcher textMatcher = p.matcher(eventMessage.getText());
					Matcher htmlMatcher = p.matcher(eventMessage.getHtml());
					
					if(subjectMatcher.find() || textMatcher.find() || htmlMatcher.find() ){
						log("matched message");
						found=true;
					} 
				}
				
				// when filtering on regex we might not match the message.  In this case do nothing.
				if(found){
					//log("found message: " + eventMessage.getSubject());
					
					log("Raising the event!");
					CFEvent event = new CFEvent(this.id);

			        // Set the function to call in the CFC.
			        event.setCfcMethod(this.functionToCall);

			        // data to return 
			        Hashtable <String, Object>data = new Hashtable<String, Object>();
			        data.put("message", eventMessage);
			        
			        // Set the data in the event
			        event.setData(data);

			        // Set a gateway type, this is a string that identifies the class of
			        event.setGatewayType("ImapWatcher");

			        // Set an ID that is used to identify the sender
			        event.setOriginatorID("");

			        // Send to each listener
			        for (String path: this.cfcListeners) {
				    	event.setCfcPath(path);

				    	this.gatewayServices.addEvent(event);
				    }
					
					// if configured to do so, mark found message as read
					if(this.properties.getProperty("markAsRead").equals("true")){
						log("mark message as read");
						message.setFlag(Flags.Flag.SEEN, true);
					}
					
					// what do we do now that the event is done?
					String postEventAction = this.properties.getProperty("postEventAction");
					log("postEventAction: " + postEventAction);
					if(postEventAction.equals("move")){
						log("move message to folder: " + this.properties.getProperty("targetFolder"));
						Message moveMessages[] = {message};
						this.folder.copyMessages(moveMessages, this.targetFolder);
						message.setFlag(Flags.Flag.DELETED, true);
						
					} else if(postEventAction.equals("delete")){
						log("delete message");
						message.setFlag(Flags.Flag.DELETED, true);
						
					} else if(postEventAction.equals("none")){
						log("do nothing");
						
					}
				} else {
					log("Message not matched by regex");
				}
			}
			//this.folder.close(true);
		} catch(Exception e) {
			log("Error announcing new message: " + e.toString(), true);
		}
		
	}
		
	@Override
	public void stop() {
		this.status = Gateway.STOPPING;
		this.shutdown = true;
		
		// TODO Stopping to stop the gateway
		log("Stopping Gateway");
		
		 // tell generator to stop
        try
        {
        	this.folder.close(false);
            listenerThread.interrupt();
            listenerThread.join(10 * 1000); // ten seconds
        }
        catch (Exception e)
        {
            // ignore
        }
		
		this.status = Gateway.STOPPED;
		
	}

	@Override
	public void messagesAdded(MessageCountEvent arg0) {
		log("Message added!");
		announceMessages();
	}

	@Override
	public void messagesRemoved(MessageCountEvent arg0) {
		// do nothing
	}

	private void log(String message, boolean override){
		if(this.loggingEnabled || override){
			logger.info("ImapWatcherGateway: " + message);
		}
	}
	
	private void log(String message){
		log(message, false);
	}

}
