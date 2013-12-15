Efficient Command-Line Accounting
=================================

Inspired by Andrew Cantino's 
[blog about accounting with Ledger and Reckon](http://blog.andrewcantino.com/blog/2013/02/16/command-line-accounting-with-ledger-and-reckon/), 
I started this year out following his workflow. I've used MS MyMoney, GnuCash, KMyMoney, and Mint before.
Ledger is my clear favorite out of these.

All of the software offerings are retty similar:
they download transactions, some do a reasonable job of classifying them, but I universally spend
longer than I want to wrangling transactions and generating reports. Mint is awesome at sucking in
transactions and categorizing them. It is focused more on budgeting than the full compliment of 
accounting functions. This is good for me, since I am no accountant; I just want to track my spending
and keep an eye on my budget. However, I was never comfortable with all the access that Mint
had to my financial life. I also wanted more flexibility with report generation than Mint offers.

Switching to the command line makes a lot of sense, since you can easily script your workflow and
spend approximately no time performing routine actions. Andrew wrote about using Reckon, a Ruby program for loading transactions from CSV and appending them to your Ledger journal. I started out using the same workflow, but I got tired fast of downloading CSV files from all my accounts. I put together [account-cli](https://github.com/vvcephei/account-cli), which is similar to Reckon, but adds the ability to download transactions straight from the bank using OFX (aka Direct Connect).

Using account-cli, here is my workflow:

1. Download recent transactions from the one account that doesn't support OFX
2. Issue this command: ```account-cli path/to/ledger/journal.dat path/to/your/config.yaml```
   This downloads new transactions, adds them to my journal, and then prints out my balance and budget reports.
3. Profit!

Having a simple 2-step process has been the major factor that keeps me on top of my finances.

How to set this up
------------------

The Ledger documentation has a really good introduction. It walks you through how to use the program, of course, but it also gives you an accessible intro to accounting.

Following Andrew's advice and also the [tutorial for ledger](http://www.ledger-cli.org/3.0/doc/ledger3.html),
I start a new Ledger file at the new year. The first block is the budget:

```
~ Monthly
  Expenses:Automotive:Gas   $300.00
  Expenses:Entertainment     $30.00
  Expenses:Food:Dining Out  $500.00
  Expenses:Food:Groceries   $250.00
  Assets

~ Yearly
  Expenses:Travel          $1500.00
  Assets
```

Then comes the opening balances:

```
2013/01/01 * Opening Balances
	Assets:Bank:Savings               $100.00
	Liabilities:Bank:Credit Card	$-100.00
	Liabilities:Student Loan	$900.00
	Equity: Opening Balances
```

Then, you just keep a running list of transactions:

```
2013/02/04	Example Transaction
	Expenses:Automotive:Gas					$1000.00
	Assets:Bank:Checking

2013/03/04	Example Transaction 2
	Expenses:Food:Dining Out				$400.00
	Assets:Bank:Checking

2013/10/04	Example Transaction 5
	Income:Work:Reimbursement			  $-100.00
	Assets:Bank:Checking

2013/11/04	Example Transaction 4
	Income:Work:Paycheck				$-3,000.00
	Assets:Bank:Checking

2013/12/04	Example Transaction 3
	Expenses:Food:Groceries				$600.00
	Assets:Bank:Checking
```

The next component is the configuration file for account-cli.
[Here](https://github.com/vvcephei/account-cli/blob/master/src/main/config/config.yaml) is the example config I put together
with comments explaining all the parts.


Let's say that journal is stored in a file called ```2013.dat```, and the config is in ```config.yaml```, and that you followed the
couple of simple steps to [install account-cli](https://github.com/vvcephei/account-cli#installingupdating).

All you have to do to update your finances is run the command:

```
account-cli 2013.dat config.yaml
```

It will download new transactions, match them against the journal, and walk you through the categorization like this:

```
+----------------------+------------+-----------+------------------------+
| Assets:Bank:Savings  | 2013/12/13 | -$1000.00 | Withdrawal to Checking |
+----------------------+------------+-----------+------------------------+
To which account did this money go? ([account] / [q]uit/ [s]kip / [1]Bank:Transfer / [2]Expenses:Utilities / [3]Expenses:Entertainment) 1


+-----------------------+------------+---------+----------------+
| Assets:Bank:Checking  | 2013/12/14 | -$80.00 | ATM Withdrawal |
+-----------------------+------------+---------+----------------+
To which account did this money go? ([account] / [q]uit/ [s]kip / [1]Expenses:Entertainment / [2]Bank:Transfer / [3]Expenses:Automotive:Fees) Expenses:Misc


+-----------------------+------------+----------+----------------------+
| Assets:Bank:Checking  | 2013/12/13 | $1000.00 | Deposit from Savings |
+-----------------------+------------+----------+----------------------+
From which account did this money come? ([account] / [q]uit/ [s]kip / [1]Bank:Transfer / [2]Income:Work:Reimbursement / [3]Income:Work:Paycheck) 1
```

Then, it prints out the reports:

```
Balances: Assets and Liabilities
           $1,200.00  Assets:Bank
           $1,100.00    Checking
             $100.00    Savings
             $800.00  Liabilities
            $-100.00    Bank:Credit Card
             $900.00    Student Loan
--------------------
           $2,000.00

Balances: Expenses and Income
           $2,000.00  Expenses
           $1,000.00    Automotive:Gas
           $1,000.00    Food
             $400.00      Dining Out
             $600.00      Groceries
          $-3,100.00  Income:Work
          $-3,000.00    Paycheck
            $-100.00    Reimbursement
--------------------
          $-1,100.00


Budget: this year
      Actual       Budget         Diff  Burn  Account
   $2,000.00   $13,380.00  $-11,380.00   15%  Expenses
   $1,000.00    $3,300.00   $-2,300.00   30%    Automotive:Gas
           0      $330.00     $-330.00     0    Entertainment
   $1,000.00    $8,250.00   $-7,250.00   12%    Food
     $400.00    $5,500.00   $-5,100.00    7%      Dining Out
     $600.00    $2,750.00   $-2,150.00   22%      Groceries
           0    $1,500.00   $-1,500.00     0    Travel
------------ ------------ ------------ -----
   $2,000.00   $13,380.00  $-11,380.00   15%
Percent of Period complete:              95%

Budget: this quarter
      Actual       Budget         Diff  Burn  Account
     $600.00    $1,080.00     $-480.00   56%  Expenses
           0      $300.00     $-300.00     0    Automotive:Gas
           0       $30.00      $-30.00     0    Entertainment
     $600.00      $750.00     $-150.00   80%    Food
           0      $500.00     $-500.00     0      Dining Out
     $600.00      $250.00      $350.00  240%      Groceries
------------ ------------ ------------ -----
     $600.00    $1,080.00     $-480.00   56%
Percent of Period complete:              82%

Budget: this month
      Actual       Budget         Diff  Burn  Account
     $600.00    $1,080.00     $-480.00   56%  Expenses
           0      $300.00     $-300.00     0    Automotive:Gas
           0       $30.00      $-30.00     0    Entertainment
     $600.00      $750.00     $-150.00   80%    Food
           0      $500.00     $-500.00     0      Dining Out
     $600.00      $250.00      $350.00  240%      Groceries
------------ ------------ ------------ -----
     $600.00    $1,080.00     $-480.00   56%
Percent of Period complete:              46%
```

As a final note, Ledger is a powerful piece of accounting software, and you can run all kinds of reports. As I discover new, useful ones, I'll update my [scripts](https://github.com/vvcephei/account-cli/tree/master/bin).

If you have any feedback or advice, I'd welcome comments and pull requests!

Cheers!
-John
