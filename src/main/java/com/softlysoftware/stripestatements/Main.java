package com.softlysoftware.stripestatements;

import java.util.Properties;
import java.util.Date;
import java.io.IOException;

public class Main {

	public static void main(String[] args) {
		log("Stripe Statements started at : " + new Date());
		Properties properties = new Properties();
		try {
			properties.load(Main.class.getClassLoader().getResourceAsStream("Stripe-Statements.properties"));
		}
		catch (IOException ioe) {
			log("Problem accessing properties file.");
			ioe.printStackTrace();
			return;
		}
		log("Property : statements.directory : " + properties.getProperty("statements.directory"));
		log("Stripe Statements ended at : " + new Date());
	}

	static void log(String string) {
		System.out.println(string);
	}

}