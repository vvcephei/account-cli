account-cli
=========

A helper for Ledger (http://www.ledger-cli.org/). 

The ```src/``` directory contains a JVM utility that does the following:
 - connect to your banks using OFX (the same protocol used by Quicken, MS Money, etc.) and download your transactions
 - load transactions from CSV files, in case your bank doesn't support OFX
 - compare the transactions to your Ledger journal, removing duplicates
 - categorize your new transactions based on the transactions already in your journal

The ```bin/``` directory contains scripts for:
 - running the account-cli code described above
 - running some convenient ledger reports

This project is inspired by the awesome reckon (https://github.com/cantino/reckon) project. I really liked the reckon workflow, but I wanted something that would go out and grab transactions from the banks.

Installing/Updating
-----

#### Linux/Mac:
```bash
# For new installs: Check out this repo
git clone https://github.com/vvcephei/account-cli.git
cd account-cli

# For updates: Pull new changes from the repo
git pull

# Build
./sbt clean assembly

# Add the scripts to your path (this goes in your shell rc or profile)
PATH=$PATH:/path/to/account-cli/bin
```

Setup
-----
1. Start a ledger file (http://www.ledger-cli.org/3.0/doc/ledger3.html#Start-a-Journal-File)
2. You don't have to, but the supporting scripts expect you to set up a budget (http://www.ledger-cli.org/3.0/doc/ledger3.html#Budgeting-and-Forecasting), which means your ledger journal will start out like this:
```
~ Monthly
  Expenses:Automotive:Gas   $150.00
  Expenses:Entertainment     $30.00
  Expenses:Food:Dining Out  $300.00
  Expenses:Food:Groceries   $250.00
  Assets

~ Yearly
  Expenses:Travel          $1000.00
  Assets

... (ledger file continues...)
```
3. Set up the config file: copy ```src/main/config/config.yaml``` to a new location (probably right next to your ledger file) and edit it to set up your accounts. See the documentation in the config.yaml for information on how to set it up.

Usage
-----
#### To download new transactions and then print a nice report:
```bash
account-cli path/to/ledger/journal.dat path/to/your/config.yaml
```

#### Other utilities:
```bash
# To just download transactions and update the journal:
get-transactions --days 40 --ledger path/to/ledger/journal.dat --config path/to/your/config.yaml

# To just run some convenient reports from ledger:
reports path/to/ledger/journal.dat

# To quickly print your budget for some period, along with your parcentage of the way through that period:
echo "this year" | budget
```

