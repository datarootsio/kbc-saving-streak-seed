package io.dataroots.savingstreak.web;

/** What a customer types to return money from savings to a named current account. */
record WithdrawalRequest(String amount, Long toCurrentAccountId) {
}
