package com.softlysoftware.stripestatements;

import java.util.Properties;
import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Calendar;
import java.util.List;
import java.util.LinkedList;
import java.util.Collections;
import java.util.Comparator;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.text.NumberFormat;
import java.text.DecimalFormat;
import java.io.IOException;
import java.io.File;
import java.io.FileFilter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import java.io.BufferedWriter;
import java.io.OutputStreamWriter;
import java.io.FileOutputStream;
import javax.activation.DataHandler;
import javax.activation.FileDataSource;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMultipart;
import com.stripe.Stripe;
import com.stripe.model.Fee;
import com.stripe.model.Money;
import com.stripe.model.Balance;
import com.stripe.model.BalanceTransaction;
import com.stripe.model.BalanceTransactionCollection;
import com.stripe.exception.StripeException;
import com.stripe.exception.AuthenticationException;
import com.stripe.exception.InvalidRequestException;
import com.stripe.exception.APIConnectionException;
import com.stripe.exception.APIException;
import com.stripe.exception.CardException;


public class Main {

	public static void main(String[] args) {
		log("Stripe Statements started at : " + new Date());
		try {
			final Properties properties = new Properties();
			properties.load(Main.class.getClassLoader().getResourceAsStream("Stripe-Statements.properties"));
			File directory = new File(properties.getProperty("statements.directory"));
			if (!directory.exists()) directory.mkdirs();
			if (!directory.isDirectory()) {
				log("ABORT : File exists, but is not a directory : " + directory);
				return;
			}
			log("Property : statements.directory : " + directory);
			final String prefix = properties.getProperty("statements.prefix");
			log("Property : statements.prefix : " + prefix);
			final String suffix = properties.getProperty("statements.suffix");
			log("Property : statements.suffix : " + suffix);
			FileFilter fileFilter = new FileFilter() {
				public boolean accept(File file) {
	               return file.getName().startsWith(prefix) && file.getName().endsWith(suffix);
	            }
	        };
			File[] extant = directory.listFiles(fileFilter);
			int sequence = 0;
			boolean gapInTheData = true;
			Set<String> seenTransactions = new HashSet<String>();
			if (extant.length == 0) {
				log("No existing statement files found. Assuming this is the first run.");
				sequence = 1;
				gapInTheData = false;
			}
			else {
				log("Found " + extant.length + " existing statement files.");
				int biggestSequence = 0;
				File mostRecent = null;
				for (int i = 0; i < extant.length; i++) {
					String name = extant[i].getName();
					try {
						int s = Integer.parseInt(name.substring(prefix.length(), name.length() - suffix.length()));
						if (s > biggestSequence) {
							biggestSequence = s;
							mostRecent = extant[i];
						}
					}
					catch (NumberFormatException nfe) {
						log("ABORT : Bad statement file name : " + name);
						return;
					}
				}
				sequence = biggestSequence + 1;
				BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(mostRecent), "UTF-8"));
				String line = in.readLine();
				while ((line = in.readLine()) != null) {
					String[] column = line.split("\t");
					seenTransactions.add(column[0]);
				}
				in.close();
				log("Identified most recent file : " + mostRecent);
				log("Scanned " + seenTransactions.size() + " transactions.");
			}
			File statement = new File(directory, prefix + sequence + suffix);
			log("Next statement file will be : " + statement);
			String stripeSecret = properties.getProperty("stripe.secret");
			log("Property : stripe.secret : " + String.format("%" + stripeSecret.length() + "s", "").replace(' ', '*'));
			int days = Integer.parseInt(properties.getProperty("stripe.days"));
			log("Property : stripe.days : " + days);
			boolean skipApplicationFees = false;
			String skipApplicationFeesProperty = properties.getProperty("stripe.skip.application.fees");
			if (skipApplicationFeesProperty != null) {
				log("Property : stripe.skip.application.fees : " + skipApplicationFeesProperty);
				skipApplicationFees = skipApplicationFeesProperty.toLowerCase().trim().equals("true");
				log("Application fees " + (skipApplicationFees?"won't":"will still") + " be included in the output.");
			}
			Calendar cal = Calendar.getInstance();
			cal.add(Calendar.DATE, days * -1);
			log("Getting transactions since : " + cal.getTime());
			Stripe.apiKey = stripeSecret;
			Map<String, Object> params = new HashMap<String, Object>();
			Map<String, Object> sub = new HashMap<String, Object>();
			sub.put("gt", cal.getTime().getTime()/1000);
			params.put("created", sub);
			params.put("count", 100);
			int offset = 0;
			List<BalanceTransaction> transactions = new LinkedList<BalanceTransaction>();
			boolean morePagesNeeded = false;
			do {
				params.put("offset", offset);
				BalanceTransactionCollection balanceTransactionCollection = BalanceTransaction.all(params);
				log("Got " + balanceTransactionCollection.getData().size() + " transactions from Stripe");
				transactions.addAll(balanceTransactionCollection.getData());
				int processed = offset + balanceTransactionCollection.getData().size();
				morePagesNeeded = processed < balanceTransactionCollection.getCount();
				if (morePagesNeeded) {
					offset = offset + 100;
					log("Have processed " + processed + " of " + balanceTransactionCollection.getData().size());
					log("Making additional call with offset = " + offset);
				}
			} while (morePagesNeeded);
			Collections.sort(transactions, new Comparator<BalanceTransaction>(){
				public int compare(BalanceTransaction o1, BalanceTransaction o2) {
					if (o1.getCreated() == o2.getCreated()) return o1.getType().compareTo(o2.getType());
					return (int)(o1.getCreated() - o2.getCreated());
				}
			});
			log("Total transactions downloaded : " + transactions.size());
			int skippedApplicationFees = 0;
			int skippedApplicationFeesTotal = 0;
			BufferedWriter out = null;
			for (BalanceTransaction tr : transactions) {
				if (seenTransactions.contains(tr.getId())) {
					gapInTheData = false;
				}
				else if (gapInTheData) {
					// data that predates that in the last statement
				}
				else if (skipApplicationFees && tr.getDescription().startsWith("Application fee from application")) {
					skippedApplicationFees++;
					skippedApplicationFeesTotal = skippedApplicationFeesTotal + tr.getAmount();
				}
				else {
					if (out == null) {
						out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(statement), "UTF-8"));
						out.write("id\tsource\tamount\tcurrency\ttype\tdate\ttime\tdescription\n");
					}
					Date created = new Date(tr.getCreated()*1000);
					out.write(tr.getId() + "\t" + tr.getSource() + "\t" + MF.format(((double)tr.getAmount())/100d) + "\t" + tr.getCurrency() + "\t");
					out.write(tr.getType() + "\t" + DF.format(created) + "\t" + TF.format(created) + "\t" + tr.getDescription() + "\n");
					for (Fee fee : tr.getFeeDetails()) {
						out.write(tr.getId() + "\t" + tr.getSource() + "\t" + MF.format((double)(fee.getAmount()*-1d)/100d) + "\t" + fee.getCurrency() + "\t");
						out.write(fee.getType() + "\t" + DF.format(created) + "\t" + TF.format(created) + "\t" + fee.getDescription() + "\n");
					}
				}
			}
			if (gapInTheData) {
				log("ABORT : hit a gap in the data - script not run frequently enough?");
				return;
			}
			if (out == null) {
				log("No new transactions. No statement file created.");
			}
			else {
				out.close();
				String to = properties.getProperty("mail.to");
				if (to != null && !to.endsWith("@example.com")) {
					log("Sending email to : " + to);
					final String username = properties.getProperty("mail.username");
					final String password = properties.getProperty("mail.password");
					Session session = Session.getInstance(properties, new javax.mail.Authenticator() {
						protected PasswordAuthentication getPasswordAuthentication() {
							return new PasswordAuthentication(username, password);
						}
					});
					try {
						MimeBodyPart textPart = new MimeBodyPart();
						textPart.setText("Attached : " + statement.getName(), "UTF-8");

						MimeBodyPart htmlPart = new MimeBodyPart();
						htmlPart.setContent("Attached : <strong>" + statement.getName() + "</strong>", "text/html; charset=UTF-8");

						MimeMultipart alternative = new MimeMultipart("alternative");
						alternative.addBodyPart(textPart);
						alternative.addBodyPart(htmlPart);

						MimeBodyPart wrap = new MimeBodyPart();
						wrap.setContent(alternative);

						MimeMultipart mixed = new MimeMultipart("mixed");
						mixed.addBodyPart(wrap);

						MimeBodyPart attachmentBodyPart = new MimeBodyPart();
						attachmentBodyPart.setDataHandler(new DataHandler(new FileDataSource(statement)));
						attachmentBodyPart.setFileName(statement.getName());
						mixed.addBodyPart(attachmentBodyPart);

						Message message = new MimeMessage(session);
						message.setFrom(new InternetAddress(properties.getProperty("mail.from")));
						message.setRecipients(Message.RecipientType.TO,	InternetAddress.parse(to));
						message.setSubject(statement.getName());
						message.setContent(mixed);

						Transport.send(message);
					}
					catch (MessagingException e) {
						throw new RuntimeException(e);
					}
				}
			}
			if (skipApplicationFees) {
				log("Total number of application fees skipped : " + skippedApplicationFees);
				log("Total amount of application fees skipped : " + skippedApplicationFeesTotal/100d);
			}
			Balance balance = Balance.retrieve();
			for (Money m : balance.getPending()) {
				log("Pending balance : " + m.getAmount() + " " + m.getCurrency());
			}
			for (Money m : balance.getAvailable()) {
				log("Available balance : " + m.getAmount() + " " + m.getCurrency());
			}
		}
		catch (AuthenticationException e) {
			log("ABORT : Authentication problems connecting to Stripe. Please check your secret in the properties file.");
			return;
		}
		catch (InvalidRequestException e) {throw new RuntimeException(e);}
		catch (APIException e) {throw new RuntimeException(e);}
		catch (APIConnectionException e) {throw new RuntimeException(e);}
		catch (CardException e) {throw new RuntimeException(e);}
		catch (UnsupportedEncodingException e) {throw new RuntimeException("Should never happen.", e);}
		catch (FileNotFoundException e) {throw new RuntimeException("Should never happen.", e);}
		catch (IOException e) {throw new RuntimeException("Problem while reading file.", e);}
		log("Stripe Statements ended at : " + new Date());
	}

	static DateFormat DF = new SimpleDateFormat("yyyy-MM-dd");
	static DateFormat TF = new SimpleDateFormat("HH:mm:ss");
	static NumberFormat MF = new DecimalFormat("0.00");

	static void log(String string) {
		System.out.println(string);
	}

}