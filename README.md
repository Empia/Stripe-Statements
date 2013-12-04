Stripe-Statements
=================

Maintain a sequential set of statement files of Stripe transactions.

We needed to get our Stipe transaction data in the form of statements to import to our accounts system ([Xero](http://www.xero.com/)).

The data exports from [Stripe's balance history page](https://manage.stripe.com/balance/history) page have two disadvantages when it comes to building a set of statements:

1. Fees are included on the same line as the amount charged
2. You have to track which date range you have previously exported

This project aims to provide a simple way to regularly build statements with just a click.

Get Started
-----------

Download the zip file of this project and extract it. In the folder, edit the proprties file to record your Stripe details. Then run the shell script.

Each time you run, if there are new transactions that haven't yet been downloaded, a new statement will be created. As long as you run the script regularly (e.g. weekly) everything should tick over smoothly. You can run as often as you like - statements won't be created if there is no new data to download.

How It Works
------------

The code will look in your nominated statements folder for files that it might have written before. If it finds none, it will assume this is the first run. Otherwise, it will read those files to find the Stripe transaction ids already processed. Then it connects to Stripe to get your recent data. By default it gets the past two weeks of data.

It will try and match the oldest transaction id from the API call with something in the files. If it can't do this, it will give up and report an error. This should help avoid there being gaps in the statements.

Then it will ignore any transactions that have appeared in the statements before. Any remaining ones get written to the new statement.
