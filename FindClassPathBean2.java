/**
 * 
 */
package com.saki.adaptermodule;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.Adler32;
import java.util.zip.CheckedOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.ejb.CreateException;
import javax.ejb.SessionBean;
import javax.ejb.SessionContext;

import com.sap.aii.af.lib.mp.module.Module;
import com.sap.aii.af.lib.mp.module.ModuleContext;
import com.sap.aii.af.lib.mp.module.ModuleData;
import com.sap.aii.af.lib.mp.module.ModuleException;

import com.sap.engine.interfaces.messaging.api.Message;
import com.sap.engine.interfaces.messaging.api.MessageDirection;
import com.sap.engine.interfaces.messaging.api.MessageKey;
import com.sap.engine.interfaces.messaging.api.PublicAPIAccessFactory;
import com.sap.engine.interfaces.messaging.api.XMLPayload;
import com.sap.engine.interfaces.messaging.api.auditlog.AuditAccess;
import com.sap.engine.interfaces.messaging.api.auditlog.AuditLogStatus;
import com.sap.engine.interfaces.messaging.api.exception.InvalidParamException;
import com.sap.engine.interfaces.messaging.api.exception.MessagingException;

public class FindClassPathBean implements SessionBean, Module {
	public static final String VERSION_ID = "$Id://tc/aii/30_REL/src/_adapters/_module/java/user/module/XMLElementEncrypt.java#1 $";
	static final long serialVersionUID = 7435850550539048631L;
	private SessionContext myContext;
	static final int BUFFER_SIZE = 2048;

	public void ejbRemove() {
	}

	public void ejbActivate() {
	}

	public void ejbPassivate() {
	}

	public void setSessionContext(SessionContext context) {
		myContext = context;
	}

	public void ejbCreate() throws CreateException {
	}

	static final int BUFFER = 2048;

	public ModuleData process(ModuleContext moduleContext,
			ModuleData inputModuleData) throws ModuleException {
		AuditAccess audit = null;

		Object obj = null;
		Message msg = null;
		MessageKey key = null;

		obj = inputModuleData.getPrincipalData();
		msg = (Message) obj;
		if (msg.getMessageDirection().equals(MessageDirection.OUTBOUND))
			key = new MessageKey(msg.getMessageId(), MessageDirection.OUTBOUND);
		else
			key = new MessageKey(msg.getMessageId(), MessageDirection.INBOUND);

		
		XMLPayload xmlpayload = msg.getDocument();
		InputStream in = msg.getMainPayload().getInputStream();

		BufferedReader reader = new BufferedReader(new InputStreamReader(in));

		ByteArrayOutputStream dest = null;
		dest = new ByteArrayOutputStream();
		CheckedOutputStream checksum = new CheckedOutputStream(dest,new Adler32());
		ByteArrayOutputStream bos = new ByteArrayOutputStream(); 
		
		ZipOutputStream out = new ZipOutputStream(bos);

		String className = "";
		Set<URL> setOfUniqueFilePaths = new HashSet<URL>();

		try {
			audit = PublicAPIAccessFactory.getPublicAPIAccess().getAuditAccess();
			while ((className = reader.readLine()) != null) {

				try {
					Class c = Class.forName(className);
					URL loc = c.getProtectionDomain().getCodeSource().getLocation();
					audit = PublicAPIAccessFactory.getPublicAPIAccess().getAuditAccess();

					audit.addAuditLogEntry(key, AuditLogStatus.SUCCESS, c+ " is found at " + loc);
					setOfUniqueFilePaths.add(loc);
				} catch (Exception e) {
					audit.addAuditLogEntry(key, AuditLogStatus.ERROR, "Class "+ className + "not found");
				}

			}
		} catch (IOException e) {
			audit.addAuditLogEntry(key, AuditLogStatus.ERROR, "Class "+ className + "not found");
		} catch (MessagingException e) {
			audit.addAuditLogEntry(key, AuditLogStatus.ERROR, "Class "+ className + "not found");
		}

		BufferedInputStream origin = null;
		byte data[] = new byte[BUFFER];

		for (URL urlFilePath : setOfUniqueFilePaths) {
			try {
				
				audit.addAuditLogEntry(key, AuditLogStatus.SUCCESS, "Reading file: " + urlFilePath.toExternalForm());
				
				File file = new File(urlFilePath.toURI());
				FileInputStream fi = new FileInputStream(file);
				origin = new BufferedInputStream(fi, BUFFER);
				ZipEntry entry = new ZipEntry(file.getName());
				
				audit.addAuditLogEntry(key, AuditLogStatus.SUCCESS, "File name is : " + file.getName());
				out.putNextEntry(entry);
				int count;
				while ((count = origin.read(data, 0, BUFFER)) != -1) {
					out.write(data, 0, count);
				}
				origin.close();

			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			} catch (URISyntaxException e) {
				e.printStackTrace();
			}
		}

		try {
			out.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

		
		byte[] outputBytes = bos.toByteArray();
		try {
			xmlpayload.setContent(outputBytes);
		} catch (InvalidParamException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		inputModuleData.setPrincipalData(msg);
		return inputModuleData;

	}

}
