package io.dataroots.savingstreak.web;

import java.util.List;

import io.dataroots.savingstreak.accounts.AccountsService;
import io.dataroots.savingstreak.accounts.CustomerAccounts;
import io.dataroots.savingstreak.accounts.SavingsAccount;
import io.dataroots.savingstreak.deposits.DepositsService;
import io.dataroots.savingstreak.points.PointsService;
import io.dataroots.savingstreak.web.CustomerAccountsResponse.CurrentAccountResponse;
import io.dataroots.savingstreak.web.CustomerAccountsResponse.SavingsAccountResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/customers")
class CustomerController {

    private final AccountsService accounts;
    private final DepositsService deposits;
    private final PointsService points;

    CustomerController(AccountsService accounts, DepositsService deposits, PointsService points) {
        this.accounts = accounts;
        this.deposits = deposits;
        this.points = points;
    }

    /**
     * Everybody this application has heard of, and the address each of them signs in with.
     *
     * <p>It exists so that a training session can sign in as somebody without first being told their
     * address, and the sign-in screen offers them as shortcuts. A bank does not publish a directory
     * of its customers to anyone who asks, and this endpoint is the first thing an authentication
     * slice would take away.
     */
    @GetMapping
    List<CustomerResponse> customers() {
        return accounts.customers().stream().map(CustomerResponse::of).toList();
    }

    /**
     * Recognises the address somebody typed and answers with the customer it belongs to.
     *
     * <p>No session is created, here or anywhere: what comes back is a customer, the browser
     * remembers it, and every request after this one still names the account it is about. Nothing
     * checks that the browser was ever told about that account. This is a sign-in screen and not
     * authentication, and the difference is the whole of what a later slice would add.
     *
     * <p>The address travels in the body rather than in the path, because a URL is written down —
     * in logs, in histories, in whatever sits between here and the browser — and a customer's
     * contact details should not be.
     */
    @PostMapping("/sign-in")
    CustomerResponse signIn(@RequestBody SignInRequest request) {
        if (request == null || request.contactDetails() == null || request.contactDetails().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Fill in the email address you bank with.");
        }
        return accounts.customerIdentifiedBy(request.contactDetails())
                .map(CustomerResponse::of)
                // The same answer for an address that is not a customer's and one that is nobody's,
                // which is all this application can honestly tell apart: it knows who exists, and
                // not who is typing. Worded for someone who mistyped their own address, because in
                // a training session that is who it will be.
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "No customer banks here under that email address."));
    }

    /**
     * Everything the customer holds and what is in it: the accounts money comes from, and the
     * accounts it goes to, with both of the figures a savings account is worth.
     *
     * <p>Those figures come from three modules that do not know about each other — who holds the
     * account, what has been paid into it, and what that earned — and are assembled here, the same
     * way the savings account's own endpoint assembles them. Assembling an answer is not a rule: no
     * decision about money or points is taken in this class.
     */
    @GetMapping("/{customerId}/accounts")
    CustomerAccountsResponse accountsOf(@PathVariable long customerId) {
        CustomerAccounts held = accounts.accountsOf(customerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "no customer with id " + customerId));
        return new CustomerAccountsResponse(
                held.currentAccounts().stream().map(CurrentAccountResponse::of).toList(),
                held.savingsAccounts().stream().map(this::worthOf).toList());
    }

    private SavingsAccountResponse worthOf(SavingsAccount account) {
        return new SavingsAccountResponse(
                account.getId(),
                deposits.moneyBalanceOf(account.getId()),
                points.balanceOf(account.getId()));
    }
}
