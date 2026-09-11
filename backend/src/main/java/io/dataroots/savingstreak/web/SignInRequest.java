package io.dataroots.savingstreak.web;

/**
 * What somebody fills in to sign in: the address they are known by, and nothing else. There is no
 * password field, here or on the customer, because there is nothing that would check one.
 */
record SignInRequest(String contactDetails) {
}
