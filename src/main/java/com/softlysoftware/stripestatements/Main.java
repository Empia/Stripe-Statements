package com.softlysoftware.stripestatements;

import java.util.Properties;
import java.util.Date;
import java.io.IOException;
import java.io.File;
import java.io.FileFilter;

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
			}
		}
		File statement = new File(directory, prefix + sequence + suffix);
		log("Next statement file will be : " + statement);
		log("Stripe Statements ended at : " + new Date());
	}

	static void log(String string) {
		System.out.println(string);
	}

}