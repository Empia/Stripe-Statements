package com.softlysoftware.stripestatements;

import java.util.Properties;
import java.util.Date;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import java.util.Calendar;
import java.io.IOException;
import java.io.File;
import java.io.FileFilter;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.UnsupportedEncodingException;
import com.stripe.Stripe;
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
		final Properties properties = new Properties();
		try {
			properties.load(Main.class.getClassLoader().getResourceAsStream("Stripe-Statements.properties"));
		}
		catch (IOException ioe) {
			log("ABORT : Problem accessing properties file.");
			ioe.printStackTrace();
			return;
		}
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
		Set<String> seenTransactions = new HashSet<String>();
		if (extant.length == 0) {
			log("No existing statement files found. Assuming this is the first run.");
			sequence = 1;
		}
		else {
			log("Found " + extant.length + " existing statement files.");
			for (int i = 0; i < extant.length; i++) {
				String name = extant[i].getName();
				try {
					int s = Integer.parseInt(name.substring(prefix.length(), name.length() - suffix.length()));
					if (s >= sequence) sequence = s + 1;
				}
				catch (NumberFormatException nfe) {
					log("ABORT : Bad statement file name : " + name);
					return;
				}
				try {
					BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(extant[i]), "UTF-8"));
					String line = in.readLine();
					while ((line = in.readLine()) != null) {
						String[] column = line.split("\t");
						seenTransactions.add(column[0]);
					}
					in.close();
				}
				catch (UnsupportedEncodingException e) {throw new RuntimeException("Should never happen.", e);}
				catch (FileNotFoundException e) {throw new RuntimeException("Should never happen.", e);}
				catch (IOException e) {throw new RuntimeException("Problem while reading file.", e);}
			}
		}
		log("Scanned " + seenTransactions.size() + " transactions.");
		File statement = new File(directory, prefix + sequence + suffix);
		log("Next statement file will be : " + statement);
		String stripeSecret = properties.getProperty("stripe.secret");
		log("Property : stripe.secret : " + String.format("%" + stripeSecret.length() + "s", "").replace(' ', '*'));
		int days = Integer.parseInt(properties.getProperty("stripe.days"));
		log("Property : stripe.days : " + days);
		Calendar cal = Calendar.getInstance();
		cal.add(Calendar.DATE, days * -1);
		log("Getting transactions since : " + cal.getTime());
		Stripe.apiKey = stripeSecret;
		Map<String, Object> params = new HashMap<String, Object>();
		Map<String, Object> sub = new HashMap<String, Object>();
		sub.put("gt", cal.getTime().getTime()/1000);
		params.put("created", sub);
		try {
			BalanceTransactionCollection balanceTransactionCollection = BalanceTransaction.all(params);
			log("Got " + balanceTransactionCollection.getCount() + " transactions from Stripe");
			for (BalanceTransaction transaction : balanceTransactionCollection.getData()) {

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
		log("Stripe Statements ended at : " + new Date());
	}

	static void log(String string) {
		System.out.println(string);
	}

}