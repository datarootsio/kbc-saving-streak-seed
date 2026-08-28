package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.accounts.Customer;

/**
 * A customer as the API reports one: something to show, something to send back, and the address they
 * would sign in with.
 */
record CustomerResponse(Long id, String name, String contactDetails) {

    static CustomerResponse of(Customer customer) {
        return new CustomerResponse(customer.getId(), customer.getName(), customer.getContactDetails());
    }
}
