<cfcomponent hint="I this is a sample CFC that handles generic Events">
	
	<cffunction name="onEvent" access="public" hint="I handle incomming events from an event gateway." output="false" returntype="void">
		<cfargument name="CFEvent" hint="I am the cfevent structure" required="true" type="struct" />
		<!---<cfset var EventMessageClass = CreateObject("Java", "java.lang.Class").forName("com.alagad.ImapWatcher.EventMessage")  />
		<cfdump var="#EventMessageClass#" output="console" />--->
		<cfset var Message = CFEvent.data.message />
		<cfset var x = 0 />
		<cfset var attachment = 0 />
		
		<cflog text="Incomming Message Received:" />
		
		<!---<cfdump var="#Message#" output="console" />--->
		<cflog text="Subject: #Message.getSubject()#" />
				
		<cflog text="From: #Message.getFrom()#" />
		<cflog text="To: #Message.getToList()#" />
		<cflog text="CC: #Message.getCCList()#" />
		<cflog text="Text Body: #Message.getText()#" />
		<cflog text="HTML Body: #Message.getHTml()#" />
		<cflog text="Attachments: #Message.getAttachments().size()#" />
		
		<cfloop from="0" to="#Message.getAttachments().size()#" index="x">
			<cfset attachment = Message.getAttachments().get(x) />
			<cflog text="Attachment #x# filename: #attachment.getFileName()#" />
			<cfset attachment.writeToDisk("/Users/dhughes/Desktop/temp") />
		</cfloop>
		
		<!---<cfdump var="#arguments.CFEvent#" output="console" />--->
		
	</cffunction>

</cfcomponent>