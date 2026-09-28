package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.accounts.CustomerRefused;
import io.dataroots.savingstreak.accounts.MonthlyIncomeRefused;
import io.dataroots.savingstreak.accounts.RecurringBillRefused;
import io.dataroots.savingstreak.automation.SavingRuleRefused;
import io.dataroots.savingstreak.budgets.BudgetRefused;
import io.dataroots.savingstreak.budgets.CategorisedBillRefused;
import io.dataroots.savingstreak.budgets.SpendRefused;
import io.dataroots.savingstreak.budgets.SpendingCategoryRefused;
import io.dataroots.savingstreak.challenges.ChallengeRefused;
import io.dataroots.savingstreak.clock.ClockRefused;
import io.dataroots.savingstreak.deposits.DepositRefused;
import io.dataroots.savingstreak.deposits.WithdrawalRefused;
import io.dataroots.savingstreak.gifting.GiftRefused;
import io.dataroots.savingstreak.goals.GoalRefused;
import io.dataroots.savingstreak.jobs.JobFailed;
import io.dataroots.savingstreak.jobs.JobRefused;
import io.dataroots.savingstreak.notifications.NotificationRefused;
import io.dataroots.savingstreak.products.NoticeRefused;
import io.dataroots.savingstreak.products.ProductRefused;
import io.dataroots.savingstreak.products.TermRefused;
import io.dataroots.savingstreak.rewards.OfferRefused;
import io.dataroots.savingstreak.rewards.RewardRefused;
import io.dataroots.savingstreak.rewards.VoucherRefused;
import io.dataroots.savingstreak.scheme.SchemeRefused;
import io.dataroots.savingstreak.sharedpots.SharedPotRefused;
import io.dataroots.savingstreak.simulation.SimulationRefused;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Reports a refusal from the domain over HTTP, and decides nothing else.
 *
 * <p>Whether a deposit can be made is the domain's answer; which status code says so is a fact about
 * this API, and this is the only place that knows it. The reason travels as written, because the
 * whole point of a refusal carrying words is that a person reads them.
 */
@RestControllerAdvice
class RefusalsAsHttp {

    private static final Logger log = LoggerFactory.getLogger(RefusalsAsHttp.class);

    /**
     * Two changes to the same row arriving at once, only one of which could be written.
     *
     * <p>The one refusal in this class that no module wrote. A balance is changed by reading it,
     * deciding, and writing it back; a version on the row means the second of two interleaving
     * writes fails instead of quietly overwriting the first, and this is where that failure is
     * reported. Nothing was moved — the whole transaction is rolled back with it — so the honest
     * answer is that it did not happen and can be asked for again.
     *
     * <p>A conflict rather than a server error, because nothing went wrong: the request was perfectly
     * good and arrived at the same instant as another one. It is also the one refusal worth asking a
     * client to retry, which is exactly what a 409 says and a 500 does not.
     *
     * <p>Warned about rather than logged quietly, and warned about here because there is nowhere
     * else: the clash is raised when the transaction commits, which is after the service that made
     * the change has returned and has nothing left to say.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ResponseEntity<ProblemDetail> twoChangesAtOnce(OptimisticLockingFailureException clash) {
        String reason = "Two changes to that account arrived at the same moment and only one of them "
                + "could be made. Nothing was moved. Please try again.";
        log.warn("request rejected reason={} clash={}", reason, clash.getMessage());
        HttpStatus status = HttpStatus.CONFLICT;
        return ResponseEntity.status(status).body(ProblemDetail.forStatusAndDetail(status, reason));
    }

    /**
     * A customer the application will not open. Two of them are a form that was not filled in,
     * which is what a 400 says; the third is a request that was perfectly well formed and that the
     * state of the application will not allow, which is what a 409 says.
     *
     * <p>A conflict rather than a bad request for the duplicate, and it is the one place in this
     * class that status appears. The distinction is worth drawing: the other two mean "fix what you
     * typed and send it again", and this one means "what you typed is fine, somebody else got there
     * first" — and a page that told a participant to correct an address that needed no correcting
     * would be sending them to look for a mistake they did not make.
     */
    @ExceptionHandler(CustomerRefused.class)
    ResponseEntity<ProblemDetail> customerRefused(CustomerRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_NAME, NO_CONTACT_DETAILS -> HttpStatus.BAD_REQUEST;
            case ALREADY_BANKS_HERE -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A declaration of monthly income the application will not keep. Always a bad request: the
     * account is in no unexpected state, the figure or the day that arrived does not describe an
     * income, and what the customer does next is type a different one — which is what a 400 asks
     * for.
     *
     * <p>A switch over the kind even with one value in it, like every other refusal here: the next
     * reason an income can be refused for arrives as a value in {@link MonthlyIncomeRefused.Kind}
     * and the compiler then asks this method what status it deserves, rather than letting it quietly
     * inherit a 400 that may not fit it.
     *
     * <p>There is no 404 here, and that is deliberate: a current account nobody has heard of is
     * refused by {@code CurrentAccountController} before any rule about income is reached, in the
     * words Accounts owns for an absent account.
     */
    @ExceptionHandler(MonthlyIncomeRefused.class)
    ResponseEntity<ProblemDetail> monthlyIncomeRefused(MonthlyIncomeRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case AGAINST_THE_RULES -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A recurring bill the application will not keep, or a change to one it will not make. A bill
     * that is not on that account is about something that is not there, which is what a 404 says; a
     * request that does not describe a bill this application will keep is a form to fix, which is
     * what a 400 says.
     *
     * <p>A bill that has been ended is a conflict rather than a bad request, the same distinction an
     * ended saving rule and an abandoned goal are drawn at and for the same reason: the request was
     * perfectly well formed — this name, this day, this amount — and it is the state of the bill
     * that will not allow it, so a page that told a customer to correct what they typed would be
     * sending them to look for a mistake they did not make.
     *
     * <p>One bill too many is a 400 and not a 409, although it is about the state of the account
     * rather than about the request, exactly as one saving rule too many is. What the customer does
     * next is end a bill they no longer pay and send this one again, which is the same shape of "fix
     * something and retry" every other 400 here asks for — and the sentence quotes the limit, so
     * there is nothing left to work out.
     *
     * <p>There is no case for a current account that does not exist, because
     * {@code RecurringBillController} vouches for the identifier in the path before a bill is ever
     * asked for, in the words Accounts owns for an absent account.
     */
    @ExceptionHandler(RecurringBillRefused.class)
    ResponseEntity<ProblemDetail> recurringBillRefused(RecurringBillRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_BILL -> HttpStatus.NOT_FOUND;
            case AGAINST_THE_RULES -> HttpStatus.BAD_REQUEST;
            case THE_BILL_IS_ENDED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A spending category the application will not keep, or a change to one it will not make. A
     * category that is not on that account is about something that is not there, which is what a 404
     * says; a request that does not describe a category this application will keep is a form to fix,
     * which is what a 400 says.
     *
     * <p>A category that has been ended is a conflict rather than a bad request, the same
     * distinction an ended bill and an abandoned goal are drawn at and for the same reason: the
     * request was perfectly well formed — this name, this category — and it is the state of the
     * category that will not allow it, so a page that told a customer to correct what they typed
     * would be sending them to look for a mistake they did not make.
     *
     * <p>A name already standing on the account is a conflict too, and it is the one here worth
     * saying out loud. It is the distinction {@code ALREADY_BANKS_HERE} draws: what the customer
     * typed is a perfectly good name and the account simply already answers to it, so what they do
     * next is use the category they have rather than hunt for a typo. The sentence names the word
     * back.
     *
     * <p>One category too many is a 400 and not a 409, although it is about the state of the account
     * rather than about the request, exactly as one bill too many is. What the customer does next is
     * end one they no longer use and send this again, which is the same shape of "fix something and
     * retry" every other 400 here asks for — and the sentence quotes the limit, so there is nothing
     * left to work out.
     *
     * <p>There is no case for a current account that does not exist, because
     * {@code SpendingCategoryController} vouches for the identifier in the path before a category is
     * ever asked for, in the words Accounts owns for an absent account.
     */
    @ExceptionHandler(SpendingCategoryRefused.class)
    ResponseEntity<ProblemDetail> spendingCategoryRefused(SpendingCategoryRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_CATEGORY -> HttpStatus.NOT_FOUND;
            case AGAINST_THE_RULES -> HttpStatus.BAD_REQUEST;
            case THE_CATEGORY_IS_ENDED, ALREADY_A_CATEGORY_HERE -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A spend the application will not record, or a correction to one it will not make. A spend that
     * is not on that account is about something that is not there, which is what a 404 says; every
     * other one is a 400, and that is a reading rather than a shrug.
     *
     * <p>A spend recorded on somebody else's account comes through that same 404 in the same
     * sentence as one that was never recorded at all, because the module answers both in one
     * sentence on purpose. This application has no authentication, so a status that told the two
     * apart would tell a guesser that the spend exists.
     *
     * <p>A name that is not one, a figure that is not an amount of money, a split that does not add
     * up and a split with too many parts in it are all "fix what you sent and send it again", which
     * is what a 400 says. Too many parts is not a 409 although it is about a limit, for the reason
     * one category too many is not: the customer changes what they are sending rather than changing
     * the account.
     *
     * <p>A short balance is a 400 too, and deliberately not a 409 or a 402. The account is in no
     * unexpected state — there is simply less in it than the spend asked for — which is the same
     * reading a deposit's {@code NOT_ENOUGH_MONEY} gets, and the sentence that comes back names both
     * figures so the customer can decide what to do about the gap.
     *
     * <p>A part naming a category that is not on the account, or one that has been ended, never
     * reaches here: that is {@code SpendingCategoryRefused}, answered above in the words and the
     * statuses that concept already owns. There is no case for a current account that does not
     * exist either, because {@code SpendController} vouches for the identifier in the path first.
     */
    @ExceptionHandler(SpendRefused.class)
    ResponseEntity<ProblemDetail> spendRefused(SpendRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_SPEND -> HttpStatus.NOT_FOUND;
            case AGAINST_THE_RULES, THE_PARTS_DO_NOT_ADD_UP, TOO_MANY_PARTS, NOT_ENOUGH_MONEY ->
                    HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A monthly budget this application will not keep, or a stop it cannot make. A category that
     * carries no figure is about something that is not there, which is what a 404 says; a figure
     * that does not describe a budget this application will keep is a form to fix, which is what a
     * 400 says.
     *
     * <p>A budget of nought is a 400 rather than being quietly accepted, and the sentence says so:
     * a category allowed to cost nothing is a category its holder has stopped budgeting, and this
     * application already has a way of saying that — one which keeps the months the figure governed.
     *
     * <p>Its own handler rather than a case added to the categories' one, because it is its own
     * refusal: a word a customer describes their money with and a figure they hold it to are
     * different declarations, and the sentence has to send them to the right screen. The refusals
     * about the <em>category</em> a budget was being put on — one that is not on this account, and
     * one that has ended — come through {@link #spendingCategoryRefused} in the sentences already
     * written for them.
     *
     * <p>There is no case for a current account that does not exist, because
     * {@code MonthlyBudgetController} vouches for the identifier in the path before a budget is ever
     * asked for, in the words Accounts owns for an absent account.
     */
    @ExceptionHandler(BudgetRefused.class)
    ResponseEntity<ProblemDetail> budgetRefused(BudgetRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_BUDGET -> HttpStatus.NOT_FOUND;
            case AGAINST_THE_RULES -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A bill this application will not put in a category, or a change to one it will not make. A
     * bill that is not on that account is about something that is not there, which is what a 404
     * says; a bill that has been ended is a conflict, the same distinction an ended bill's name and
     * an ended category are drawn at and for the same reason — the request was perfectly well formed
     * and it is the state of the bill that will not allow it, so a page that told a customer to
     * correct what they typed would be sending them to look for a mistake they did not make.
     *
     * <p>Its own handler rather than a case added to the categories' one, because it is its own
     * refusal: these two kinds are about the bill, and the sentence a customer reads has to send
     * them to the right list. The refusals about the <em>category</em> a bill was being put in come
     * through {@link #spendingCategoryRefused} in the sentences already written for them.
     *
     * <p>There is no case for a current account that does not exist, because
     * {@code CategorisedBillController} vouches for the identifier in the path before a bill's
     * category is ever asked for, in the words Accounts owns for an absent account.
     */
    @ExceptionHandler(CategorisedBillRefused.class)
    ResponseEntity<ProblemDetail> categorisedBillRefused(CategorisedBillRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_BILL -> HttpStatus.NOT_FOUND;
            case THE_BILL_IS_ENDED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A deposit the application will not make. Never a server error: nothing went wrong, the answer
     * is no, and the person who asked is the one who can act on it.
     */
    @ExceptionHandler(DepositRefused.class)
    ResponseEntity<ProblemDetail> depositRefused(DepositRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            // An identifier for something that is not there, which is what a 404 says.
            case NO_SUCH_ACCOUNT -> HttpStatus.NOT_FOUND;
            // Real accounts, and a request they cannot be asked to honour.
            case AGAINST_THE_RULES -> HttpStatus.BAD_REQUEST;
            // Not a conflict: the account is in no unexpected state, there is simply less in it
            // than the customer asked to move, and the sentence that comes back says how much.
            case NOT_ENOUGH_MONEY -> HttpStatus.BAD_REQUEST;
            // The first 403 in this application, and the right one: the request is understood, the
            // accounts are real, and the person making it is not allowed to. A shared pot takes
            // money from its members and from nobody else, and somebody who is not one — or who is
            // only watching — has nothing to correct in what they typed, which is what a 400 would
            // send them off to look for. Nor is it a 404: telling them the account is not there
            // would be a lie they could disprove by reading the pot.
            case NOT_ALLOWED -> HttpStatus.FORBIDDEN;
            // And a conflict for the pot that is over, which is the status a shared pot already
            // answers every "the pot is closed" refusal with. Not a 403, although it arrives
            // through the same pairing: nothing about the person asking would change the answer,
            // so there is no position for them to be refused from.
            case THE_POT_IS_CLOSED -> HttpStatus.CONFLICT;
            // And a conflict for the account whose own agreement has ended, which is the status
            // this application already answers every "it is closed" with — a closed pot here, and a
            // second press on the closing button in the catalogue. Nothing was typed wrong and
            // nothing is missing: the account is real, the sentence names the day it closed, and
            // what to do next is pay into another one.
            case THE_ACCOUNT_IS_CLOSED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A withdrawal the application will not make.
     *
     * <p>{@code MONEY_IS_SPOKEN_FOR} is a bad request for the same reason {@code NOT_ENOUGH_MONEY}
     * is, and for the reason a goal's {@code NOT_ENOUGH_UNALLOCATED} is: nothing is in an unexpected
     * state, the customer asked to take out more than is theirs to take out, and the sentence that
     * comes back says how much is. A conflict would tell them something is wrong with the account
     * when what is wrong is the figure they typed — and what they do next is either type a smaller
     * one or go and free money from a goal, which is what the sentence tells them.
     *
     * <p>{@code AGAINST_THE_AGREEMENT} joins them, and is a bad request for the third time over:
     * the account is perfectly real, the money is in it and no goal has claimed it, and the
     * condition the product attaches says not yet. The spec asks for exactly this — a withdrawal
     * refused by notice, a term or a floor is a bad request with a sentence naming which — and the
     * sentence is the domain's own, saying how many days are left rather than only that the answer
     * is no. A conflict would suggest the account had got into a state somebody should investigate,
     * when what has happened is that a customer met an agreement they signed.
     */
    @ExceptionHandler(WithdrawalRefused.class)
    ResponseEntity<ProblemDetail> withdrawalRefused(WithdrawalRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_ACCOUNT -> HttpStatus.NOT_FOUND;
            case AGAINST_THE_RULES, NOT_ENOUGH_MONEY, MONEY_IS_SPOKEN_FOR, AGAINST_THE_AGREEMENT ->
                    HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A move of the clock the application will not make. Always a bad request: the clock is in no
     * unexpected state, somebody asked it to go somewhere it does not go, and the sentence that comes
     * back says why not.
     *
     * <p>Handled here alongside the refusals a customer can cause even though only the development
     * profile can raise it, so that every refusal in this application answers in one shape and there
     * is one place to read what those shapes are.
     */
    @ExceptionHandler(ClockRefused.class)
    ResponseEntity<ProblemDetail> clockRefused(ClockRefused refusal) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refusal.getMessage()));
    }

    /**
     * A request to run a job the application will not act on — no job answers to that name, or more
     * than one does. A bad request rather than a missing page: the endpoint is there, it is the name
     * in it that is wrong, and the sentence that comes back lists the names that are right.
     *
     * <p>Told apart from a 404 deliberately. Outside the development profile these paths do not
     * exist at all, and a wrong name answering with the same status as a missing route would leave a
     * participant unable to tell "you spelled it wrong" from "this application has no such feature".
     */
    @ExceptionHandler(JobRefused.class)
    ResponseEntity<ProblemDetail> jobRefused(JobRefused refusal) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, refusal.getMessage()));
    }

    /**
     * A job that was found, run, and threw. The one thing here that really is a server error: the
     * request was fine and the job broke, which is a participant's own code failing and the most
     * useful thing this API can do is say so in the sentence rather than answer an empty 500.
     */
    @ExceptionHandler(JobFailed.class)
    ResponseEntity<ProblemDetail> jobFailed(JobFailed failure) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, failure.getMessage()));
    }

    /**
     * A challenge the application will not enrol somebody in, or an enrolment it will not end.
     *
     * <p>Two kinds of mistake and the same two statuses the rest of this class draws them with. A
     * customer nobody has heard of and a challenge code the bank has never issued are both about
     * something that is not there, which is a 404 — and the challenge is named back in the sentence,
     * so a page that has been open since before a season closed can see which one it sent.
     *
     * <p>Being in it already, and not being in it at all, are conflicts rather than bad requests.
     * The request was perfectly well formed — this customer, this challenge — and it is the state of
     * the enrolment that will not allow it, so a page that told a participant to correct what they
     * typed would be sending them to look for a mistake they did not make. It is also the honest
     * status for the way these two actually happen: a tab left open on one device while the customer
     * joined or left something on another.
     *
     * <p>A one-off somebody has already finished joins them, and is a conflict for the same reason
     * rather than a 403: nothing about the customer forbids the request, and everything about it
     * was well formed — it is the finished enrolment sitting in their own history that will not
     * allow a second one. The sentence names the challenge, which is what a page still showing an
     * enrol button needs in order to say which card to redraw.
     *
     * <p>A season that has not opened and one that has closed are conflicts too, and neither is a
     * 404 although hiding a challenge outside its window would be one way to build it. The card is
     * deliberately readable before and after the season — a campaign a customer can see coming is
     * the reason they come back in January — so the challenge is emphatically there, and it is the
     * window round it that will not allow the request. Both sentences name the date, because what
     * the customer does next is come back on it or accept that it has gone, and neither is
     * something they could work out from a status code.
     */
    @ExceptionHandler(ChallengeRefused.class)
    ResponseEntity<ProblemDetail> challengeRefused(ChallengeRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_CUSTOMER, NO_SUCH_CHALLENGE -> HttpStatus.NOT_FOUND;
            case ALREADY_ENROLLED, NOT_ENROLLED, ALREADY_DONE_AND_NOT_REPEATABLE,
                    THE_SEASON_HAS_NOT_OPENED, THE_SEASON_HAS_CLOSED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A reward the application will not hand over. Not enough points is a bad request rather than a
     * conflict: the account is in no unexpected state, the customer asked for something that costs
     * more than they have, and the sentence that comes back says how much more.
     *
     * <p>A code the catalogue has never heard of is a bad request and emphatically not a 404,
     * although it is about something that is not there. It is the status that sentence has always
     * been sent with, back when this endpoint's controller parsed the code itself, and it is the
     * right one: the claim is a form with a wrong word in it rather than a request for a page that
     * does not exist, and what the customer does next is pick something that is in the catalogue.
     * A 404 would also be indistinguishable from the customer not existing, which is the other
     * thing this same request can be wrong about and a completely different thing to go and fix.
     *
     * <p>An offer that is not on sale is a conflict, and it is the one refusal here where nothing
     * the customer sent was wrong. The code is a real code, they can afford it, and the catalogue
     * simply is not selling it — because it is still a draft, or because somebody withdrew it
     * while the page was open. That is a disagreement about the state of the thing rather than
     * about the request, which is what a 409 says and what an ended bill, a closed season and a
     * closed pot are all already reported with. A 400 would tell the page to correct the word it
     * sent, and there is nothing to correct.
     */
    @ExceptionHandler(RewardRefused.class)
    ResponseEntity<ProblemDetail> rewardRefused(RewardRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_CUSTOMER -> HttpStatus.NOT_FOUND;
            case NO_SUCH_OFFER, NOT_ENOUGH_POINTS -> HttpStatus.BAD_REQUEST;
            // A window is a conflict for the same reason not being on sale is: nothing about the
            // request is malformed and nothing about it would be improved by sending it again as
            // it stands. The offer is real, the code is right, and the state of the world says no
            // — which is exactly the difference a 409 draws and a 400 does not. An offer that has
            // not opened is also the one refusal here that becomes claimable by waiting, and
            // telling a page its request was bad would be telling it to stop asking.
            // And who an offer is for is a conflict for the same reason again, with one
            // difference worth saying: unlike a window, this one does not become claimable by
            // waiting — it becomes claimable by saving, by holding a run of weeks, or by
            // winning a badge. It is still not a bad request. The code is right, the body is
            // right, and what says no is who the customer is today, which is a state and not a
            // typo. A 400 would tell the page to stop asking, and the whole point of showing the
            // offer locked rather than hiding it is that the customer is meant to come back.
            case NOT_ON_SALE, NOT_OPEN_YET, CLOSED, NOT_FOR_YOU -> HttpStatus.CONFLICT;
            // A limit is a conflict for the same reason, with one thing added: this is the first
            // refusal here that another person would not have been given for the same request at
            // the same moment. The code is right, the offer is real and open, and what says no is
            // this customer's own history — which is a state rather than a malformed form, and is
            // exactly the difference a 409 draws. Not a 403 either, tempting as "you may not have
            // this" sounds: there is no authentication in this application and nothing was
            // forbidden to anybody, they have simply already had theirs.
            case YOU_HAVE_HAD_YOUR_LIMIT -> HttpStatus.CONFLICT;
            // Sold out is a conflict on the same argument and is the clearest case of it: the
            // code is right, the form is right, and what says no is that somebody else got there
            // first — which is the very thing a 409 was invented for. It is also, like a window
            // that has not opened, a refusal that can stop being one without the customer doing
            // anything at all, because an administrator raising the stock unlocks it; a 400 would
            // be telling a page that the request was wrong and to stop asking.
            case NOTHING_LEFT -> HttpStatus.CONFLICT;
            // And a hold that is no longer there is a conflict on the same argument, arrived at
            // from the other direction: this is the one refusal in the set where the customer
            // was, at some point, genuinely entitled to the thing. The address is right, the
            // code is right and the customer is real — what says no is that seventy-two hours
            // went by, or that they gave it up, or that they have already claimed it. A 404
            // would be the tempting alternative and would be wrong twice over: it would say the
            // address was wrong when it was not, and it would be indistinguishable from the
            // offer itself not existing, which is a different sentence with a different thing to
            // do about it. A 410 was considered and rejected for the same reason a withdrawn
            // offer does not get one: nothing here is a resource that has been removed, it is a
            // state of the world that a page should re-read and redraw.
            case THE_HOLD_HAS_LAPSED -> HttpStatus.CONFLICT;
            // And a place in a queue that is no longer there is the same argument again, which
            // is why it is the line below and not a case added to the one above. The two are
            // one fact about two rows — the thing you asked to act on is not there — and they
            // get one status for it. They are nevertheless two kinds, because the name is what
            // a page switches on and what a log line carries: a customer leaving a queue has no
            // hold and may never have had one, and reporting their missing place as a lapsed
            // hold would be this layer passing on a word about a row that does not exist. The
            // sentence says which of the three absences it is, including the one that is good
            // news: their turn came and the place became a hold.
            case YOU_ARE_NOT_IN_THAT_QUEUE -> HttpStatus.CONFLICT;
            // Already in the queue is a conflict, and it is the one refusal in this whole set
            // that is good news. Nothing about the request was wrong — they wanted to be in
            // line and they are in line, which the sentence says along with where they stand —
            // and what a page does next is redraw the card it has clearly been showing since
            // before they joined on another tab. A 400 would tell it to correct something, and
            // there is nothing to correct; a 200 would be this application pretending a second
            // place had been created when the whole point is that it has not.
            case ALREADY_WAITING -> HttpStatus.CONFLICT;
            // And a queue for something that has not run out is a conflict on exactly the
            // argument sold out is, arrived at from the opposite side: the code is right, the
            // customer is real, and what says no is the state of the stock — which has
            // changed, between the page being drawn and the button being pressed, in the
            // direction the customer wanted. That is a card to re-read rather than a form to
            // correct, and the sentence tells them the thing they can do instead, which is
            // simply claim it.
            case NOT_SOLD_OUT -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A change to the catalogue the application will not make, reported to whoever runs the
     * scheme rather than to a customer.
     *
     * <p>A code already in use is a conflict for the reason an address somebody already banks
     * under is: nothing about the request is malformed, and what the administrator does next is
     * edit the offer that is in the way or pick another code. A withdrawn offer is a conflict for
     * the reason an ended bill and an ended category are — the thing they are trying to change is
     * over, and that is a fact about its state rather than about their form.
     *
     * <p>Everything else is a bad request, because everything else genuinely is the form: a
     * missing title, a price below a point, no voucher prefix, a description longer than the
     * column that holds it, a rename of the one field an offer may never rename, or a bundle
     * naming something that cannot go into one — itself, an offer nobody has heard of, one that
     * has been withdrawn, another bundle, fewer than two of them, or fewer than one of any.
     */
    @ExceptionHandler(OfferRefused.class)
    ResponseEntity<ProblemDetail> offerRefused(OfferRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case CODE_ALREADY_TAKEN, THE_OFFER_IS_WITHDRAWN -> HttpStatus.CONFLICT;
            case AGAINST_THE_RULES -> HttpStatus.BAD_REQUEST;
            // A stock figure below what has already gone out is a conflict rather than a bad
            // form, and the distinction is the one drawn just above: forty is a perfectly good
            // number of hampers and nothing about the request is malformed. What says no is the
            // state of the application — fifty-one of them are already out there — which is a
            // fact the administrator did not have when they typed, and the sentence hands it to
            // them. A 400 would send somebody hunting for a mistake in a number that has none.
            case STOCK_BELOW_WHAT_IS_ALREADY_OUT -> HttpStatus.CONFLICT;
            // A bundle naming an offer the catalogue has never heard of is a bad request, and
            // emphatically not a 404 although it is about something that is not there. It is
            // the same argument a customer's claim for an unknown code already gets, one
            // handler above: the address is a real one and what arrived is a form with a wrong
            // word in it, so what the administrator does next is correct the word. A 404 would
            // be saying that /api/admin/rewards is missing, which it is not, and would be
            // indistinguishable from the offer named in the path being gone.
            case NO_SUCH_MEMBER -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A voucher the application will not act on at a counter. One of them is a code that names
     * nothing, which is what a 404 says; the other is a real voucher in a state that will not
     * allow what was asked, which is what a 409 says.
     *
     * <p>A conflict rather than a bad request for the used one, and the distinction is the same
     * one the duplicate customer above draws. "Fix what you typed and send it again" is a bad
     * request; "what you typed is fine and the state of the application will not allow it" is a
     * conflict — and a counter told to correct a code that needed no correcting would go looking
     * for a mistake nobody made, while the true answer is that somebody has already had the thing.
     *
     * <p>Not a 410 either, tempting though it is for a spent voucher. The resource has not gone:
     * the reading endpoint still answers for it, and the page after the refusal shows the day it
     * was used, which is the whole of what the person at the till needs to say out loud.
     *
     * <p><strong>An expired voucher is a conflict too, and for the same reason.</strong> The code
     * is real, it was typed correctly and the resource is still there — the reading endpoint
     * answers for it, with the day it ran out on — so the only thing wrong is the state the
     * application is in, which is precisely what a 409 says. Not a 410 for this one either,
     * tempting as "gone" sounds for something that has expired: 410 says the address has no
     * representation any more, and this one has a perfectly good representation that a counter
     * needs to read out loud. A 400 would send somebody hunting for a typo in a code that has
     * none.
     *
     * <p><strong>A cancelled voucher is the third conflict, and the argument is the same one a
     * third time.</strong> The code is real, it was typed correctly, the reading endpoint
     * answers for it and carries the reason somebody typed when they revoked it — so the only
     * thing wrong is the state, which is what a 409 says. It is not a 410 for the reason neither
     * of the two above is, and it is emphatically not a 404: telling a till that a cancelled code
     * does not exist would send them hunting for a typo while the true answer, and the only one
     * worth reading out to the person in front of them, is already in the sentence.
     *
     * <p>A switch over the kind, like every other refusal here, and this one is the mechanism
     * having done its job twice: the third kind arrived with the slice that learned to expire a
     * voucher and the fourth with the slice that learned to cancel one, each stopping that slice
     * on this line until somebody chose what it deserves. The set is now closed — a voucher is
     * issued and then it is used, expired or cancelled — so there is no fifth to predict.
     */
    @ExceptionHandler(VoucherRefused.class)
    ResponseEntity<ProblemDetail> voucherRefused(VoucherRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_VOUCHER -> HttpStatus.NOT_FOUND;
            case ALREADY_USED, THE_VOUCHER_HAS_EXPIRED, THE_VOUCHER_WAS_CANCELLED ->
                    HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A read of somebody's notifications the application will not answer. One reason so far, and it
     * is about somebody who is not there, which is what a 404 says.
     *
     * <p>A switch over the kind even with one value in it, like every other refusal here: the next
     * reason a notification read can be refused for arrives as a value in
     * {@link NotificationRefused.Kind} and the compiler then asks this method what status it
     * deserves, rather than letting it quietly inherit the 404 that only fits an absence.
     */
    @ExceptionHandler(NotificationRefused.class)
    ResponseEntity<ProblemDetail> notificationRefused(NotificationRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_CUSTOMER -> HttpStatus.NOT_FOUND;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * Something about a savings product the application will not do. One of them is about a product
     * the bank does not sell, which is what a 404 says; four are a form to go back and fix, which
     * is what a 400 says; the last is a request that is perfectly well formed and that the versions
     * already published will not allow, which is what a 409 says.
     *
     * <p><strong>A closed product is a 409 and not that 404</strong>, and that is the distinction
     * worth drawing. A product closed to new accounts exists, customers are holding it, and its
     * card is on the screen the request came from with a flag saying the door is shut — so nothing
     * about what was sent is wrong, and it is the state of the catalogue that will not have it.
     * Answering 404 would send somebody off to check the spelling of a product sitting in front of
     * them. The 404 above means the code was never a product at all.
     *
     * <p><strong>The two refusals about an account are both 409s, for the same reason.</strong> An
     * account that still holds money and an account that was closed already are both perfectly good
     * requests about a real account that the state of that account will not allow — take the money
     * out, or look at a screen that has caught up. Neither is a form to correct and neither is an
     * absence: the account is right there. Choosing nothing at all is the one of the four that
     * <em>is</em> a form to finish, and is the 400.
     *
     * <p><strong>The date that reads backwards is the one 409 here, and it is worth arguing.</strong>
     * A version dated before the one it follows is not a mistyped figure and not an empty box:
     * everything in the form is a perfectly good answer, and what refuses it is the history the
     * product already has. That is the same distinction the duplicate customer draws next door —
     * "what you typed is fine, the state of the application will not have it" — and it tells the
     * administrator something a 400 would not: there is nothing to correct on the form except in
     * the light of a version somebody else published.
     *
     * <p>A switch over the kind, like every other refusal here: the next reason a product can be
     * refused for arrives as a value in {@link ProductRefused.Kind} and the compiler then asks this
     * method what status it deserves, rather than letting it quietly inherit the 404 that only fits
     * an absence.
     */
    @ExceptionHandler(ProductRefused.class)
    ResponseEntity<ProblemDetail> productRefused(ProductRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_PRODUCT -> HttpStatus.NOT_FOUND;
            case A_FIGURE_THESE_TERMS_CANNOT_CARRY, A_VERSION_WITH_NO_DAY_IT_TAKES_EFFECT,
                 A_VERSION_THAT_DOES_NOT_SAY_WHAT_CHANGED, AN_ENDING_THIS_BANK_DOES_NOT_OFFER,
                 NO_PRODUCT_CHOSEN, AN_AMOUNT_NO_PROJECTION_CAN_BE_MADE_ON ->
                    HttpStatus.BAD_REQUEST;
            case A_VERSION_DATED_BEFORE_THE_ONE_BEFORE_IT, A_PRODUCT_CLOSED_TO_NEW_ACCOUNTS,
                 AN_ACCOUNT_THAT_STILL_HOLDS_MONEY, AN_ACCOUNT_ALREADY_CLOSED ->
                    HttpStatus.CONFLICT;
            // The three refusals of taking newer terms, appended rather than folded into the line
            // above so that the arm they join is a line a reader can follow to the values it was
            // written for. Both are conflicts and neither is a bad request: nothing was typed — the
            // press carries no body at all — so there is no box to go back and fix, and what will
            // not have the request is the state of the agreement in one case and of the catalogue
            // in the other. A term still running is the same reading a balance that has not been
            // emptied gets one value up; an account already on the version on offer is the same
            // reading a second closing gets, which is a page acting on what was true when it was
            // drawn.
            case AN_ACCOUNT_LOCKED_INTO_A_TERM, A_TERM_WHOSE_DAY_HAS_COME,
                 AN_ACCOUNT_ALREADY_ON_THE_TERMS_ON_OFFER -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A gift the application will not make. Two of them are about somebody who is not there, which
     * is what a 404 says; the other three are about a real pair of customers and a request they
     * cannot be asked to honour.
     *
     * <p>Not enough points is a bad request rather than a conflict, for the reason a claim's is:
     * nothing is in an unexpected state, the customer tried to give away more than they hold, and
     * the sentence that comes back says how much they have.
     */
    @ExceptionHandler(GiftRefused.class)
    ResponseEntity<ProblemDetail> giftRefused(GiftRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_CUSTOMER, NO_SUCH_RECIPIENT -> HttpStatus.NOT_FOUND;
            case TO_YOURSELF, NOT_A_NUMBER_OF_POINTS, NOT_ENOUGH_POINTS -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * Something about a shared pot the application will not do. Two of them are about something that
     * is not there — a pot nobody has heard of and a customer nobody has heard of — which is what a
     * 404 says; the third is a form to fix, which is what a 400 says.
     *
     * <p>A pot with no name is a 400 rather than a 409 for the reason a goal with no name is: the
     * pot is in no unexpected state, what arrived does not describe a pot this application will
     * open, and what the customer does next is type a name — which is exactly what a 400 asks for.
     *
     * <p><strong>The 403 in this method is the only one in this application</strong>, and invitations
     * are the slice that brought it. A contributor who tries to invite somebody, and a customer who
     * answers an invitation addressed to somebody else, have each made a request this application
     * understood, from a caller it knows, that they are not allowed to make. That is not a 404 —
     * telling a member that the pot or the invitation they can see does not exist would be a lie they
     * could disprove by refreshing the page — and it is not a 400, because there is nothing about
     * what they typed to fix. Every kind here arrives with the rule that raises it, and the switch is
     * exhaustive so that the compiler asks this method what each new one deserves rather than letting
     * it inherit a status that may not fit.
     *
     * <p>The three conflicts are the pot's state refusing a perfectly good request: somebody is
     * already a member, already waiting to answer, or has already answered. A page that told the
     * customer to correct what they typed would be sending them to look for a mistake they did not
     * make — the same distinction {@code ALREADY_BANKS_HERE} and an abandoned goal are drawn at.
     *
     * <p>An invitation to yourself is a 400 and not a 409, which is the line gifting already draws
     * for the same mistake: nothing is in an unexpected state, and what the person does next is type
     * somebody else's address.
     *
     * <p>A role change about somebody who is not in the pot is a 404 and not a 403, because it is an
     * absence rather than a permission: there is no membership there to change, and the owner who
     * asked is perfectly entitled to change the roles of the people who are. The last owner giving
     * up the role joins the conflicts above for the reason they are there — the request is good and
     * it is the pot that will not allow it — and it is the one refusal in this method that exists to
     * stop something that cannot be undone.
     *
     * <p>Withdrawal proposals brought the last three. A proposal nobody has heard of is an absence
     * like every other, which is a 404. A proposal for more than the pot holds is a 400 rather than
     * a 409, for the reason a deposit's {@code NOT_ENOUGH_MONEY} is: the pot is in no unexpected
     * state, there is simply less in it than the member asked for, and the sentence says how much —
     * so what they do next is type a smaller figure. A proposal that has already been approved,
     * rejected or taken back is a 409, because the request was perfectly well formed and it is the
     * proposal's state that will not allow it. Who may propose and who may take a proposal back are
     * refused through {@code NOT_ALLOWED} above, in the 403 roles already answer in.
     *
     * <p>Closing brought the last one, and it joins the conflicts for the reason they are all there:
     * a request made on a pot an owner has closed is a perfectly good request that the state of the
     * pot will not allow. It is deliberately not a 404 — a closed pot is a record and every read of
     * it still answers 200, so telling somebody it is not there would be a lie they could disprove
     * by refreshing the page.
     */
    @ExceptionHandler(SharedPotRefused.class)
    ResponseEntity<ProblemDetail> sharedPotRefused(SharedPotRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_POT, NO_SUCH_CUSTOMER -> HttpStatus.NOT_FOUND;
            case AGAINST_THE_RULES -> HttpStatus.BAD_REQUEST;
            case NO_SUCH_INVITATION, NO_SUCH_RECIPIENT -> HttpStatus.NOT_FOUND;
            case NOT_ALLOWED, NOT_THEIR_INVITATION -> HttpStatus.FORBIDDEN;
            case ALREADY_A_MEMBER, ALREADY_INVITED, ALREADY_ANSWERED -> HttpStatus.CONFLICT;
            case TO_YOURSELF -> HttpStatus.BAD_REQUEST;
            case NO_SUCH_MEMBER -> HttpStatus.NOT_FOUND;
            case LAST_OWNER -> HttpStatus.CONFLICT;
            case NO_SUCH_PROPOSAL -> HttpStatus.NOT_FOUND;
            case MORE_THAN_THE_POT_HOLDS -> HttpStatus.BAD_REQUEST;
            case THE_PROPOSAL_IS_CLOSED -> HttpStatus.CONFLICT;
            case POT_IS_CLOSED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A change to somebody's savings goals the application will not make. A goal that is not on that
     * account is about something that is not there, which is what a 404 says; a change that does not
     * describe a goal this application will keep is a form to fix, which is what a 400 says.
     *
     * <p>A goal that has been given up on is a conflict rather than a bad request, and it is the one
     * here worth drawing apart from the other two. The request was perfectly well formed — this name,
     * this target, this goal — and it is the state of the goal that will not allow it, so a page that
     * told a participant to correct what they typed would be sending them to look for a mistake they
     * did not make. The same distinction {@code ALREADY_BANKS_HERE} draws above.
     *
     * <p>The two refusals about money are bad requests rather than conflicts, for the reason a
     * deposit's {@code NOT_ENOUGH_MONEY} is: nothing is in an unexpected state, the customer asked to
     * move more than there is to move, and the sentence that comes back says how much there is. What
     * they do next is type a smaller figure, which is what a 400 asks for.
     *
     * <p>There is no case for a savings account that does not exist, because this module never
     * raises one: it reads no other module, so it cannot tell a real account from a made-up one, and
     * {@code SavingsGoalController} vouches for the identifier before a goal is ever asked for.
     */
    @ExceptionHandler(GoalRefused.class)
    ResponseEntity<ProblemDetail> goalRefused(GoalRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_GOAL -> HttpStatus.NOT_FOUND;
            case AGAINST_THE_RULES, NOT_ENOUGH_UNALLOCATED, MORE_THAN_THE_GOAL_NEEDS ->
                    HttpStatus.BAD_REQUEST;
            case THE_GOAL_IS_CLOSED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A saving rule the application will not leave standing, or a change to one it will not make. A
     * rule that is not on that account is about something that is not there, which is what a 404
     * says; a request that does not describe a rule this application will keep is a form to fix,
     * which is what a 400 says.
     *
     * <p>A rule that has been ended is a conflict rather than a bad request, the same distinction
     * an abandoned goal is drawn at and for the same reason: the request was perfectly well formed —
     * this name, this day, this amount — and it is the state of the rule that will not allow it, so
     * a page that told a participant to correct what they typed would be sending them to look for a
     * mistake they did not make.
     *
     * <p>One rule too many is a 400 and not a 409, although it is about the state of the account
     * rather than about the request. What the customer does next is end a rule they no longer want
     * and send this one again, which is the same shape of "fix something and retry" every other 400
     * here asks for — and the sentence quotes the limit, so there is nothing left to work out.
     *
     * <p>There is no case for a savings account that does not exist, because this module never
     * raises one: it never asks whether an account is real, and {@code SavingRuleController} vouches
     * for the identifier before a rule is ever asked for.
     */
    @ExceptionHandler(SavingRuleRefused.class)
    ResponseEntity<ProblemDetail> savingRuleRefused(SavingRuleRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            // A goal named in a split that is not being saved towards on the account is a thing
            // somebody named that is not there, exactly as a rule or a goal named in the path or in
            // an allocation's body is, and it answers the same way. The rule itself is still a rule
            // this application would keep; the goal is the part that is missing.
            case NO_SUCH_RULE, NO_SUCH_GOAL_IN_THE_SPLIT -> HttpStatus.NOT_FOUND;
            case AGAINST_THE_RULES -> HttpStatus.BAD_REQUEST;
            case THE_RULE_IS_ENDED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A question about a future the application will not fold. Always a bad request: nothing is in
     * an unexpected state, nothing is missing, and what the customer does next is ask for a different
     * change — which is exactly what a 400 asks for.
     *
     * <p>That includes a change this application can name and cannot yet answer about. It is still a
     * 400 rather than a 501, because 501 is a sentence about the verb and this is a sentence about
     * what was asked for; and the reason says so plainly rather than pretending the change does not
     * exist, which would send somebody hunting for a typo in a word the application itself printed
     * beside the box.
     *
     * <p>A switch over the kind with one value in it, like every other refusal here: the next reason
     * a future can be refused for arrives as a value in {@link SimulationRefused.Kind} and the
     * compiler then asks this method what status it deserves, rather than letting it quietly inherit
     * a 400 that may not fit it.
     *
     * <p>There is no 404 here, and that is deliberate: a savings account nobody has heard of is
     * refused by {@code SimulationController} before any question about a future is reached, in the
     * words Accounts owns for an absent account.
     *
     * <p><strong>The one refusal in this class that carries members of its own.</strong> A question
     * about the future can hold four scenarios of ten changes each, so "that day is not inside the
     * year this simulation is drawn over" is a true sentence that leaves a customer looking at forty
     * boxes. {@code scenario}, {@code changeNumber} and {@code change} say which column and which
     * chip, and they are members beside the reason rather than a prefix inside it so that the
     * objection reads the same however many futures were asked about at once — the argument is on
     * {@link SimulationRefused}. Extension members are what a problem detail has for this, and they
     * are absent rather than null on a refusal about the asking as a whole, because a page that has
     * nothing to point at should be given nothing rather than an empty string to test for.
     */
    @ExceptionHandler(SimulationRefused.class)
    ResponseEntity<ProblemDetail> simulationRefused(SimulationRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case AGAINST_THE_RULES -> HttpStatus.BAD_REQUEST;
        };
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, refusal.getMessage());
        refusal.scenario().ifPresent(scenario -> problem.setProperty("scenario", scenario));
        refusal.changeNumber().ifPresent(number -> problem.setProperty("changeNumber", number));
        refusal.change().ifPresent(change -> problem.setProperty("change", change));
        return ResponseEntity.status(status).body(problem);
    }

    /**
     * Notice this application will not give or will not take back.
     *
     * <p>Appended at the end of this class rather than filed beside the other Products refusal, and
     * that is not laziness: what an administrator may publish about a product and what one customer
     * has given notice on their own account are two subjects that happen to be kept by one module,
     * and a reader following either one arrives at a handler of its own.
     *
     * <p>A notice numbered for another account is a 404, in the same reading every other nested
     * resource here takes of an identifier that does not belong where it was sent: from outside,
     * somebody else's notice and a notice that never existed are indistinguishable, and saying
     * which would tell the asker something about an account that is not theirs.
     *
     * <p>A notice that is finished — cancelled already, or used in full by a withdrawal — is a
     * conflict rather than a bad request, the same distinction an abandoned goal and an ended rule
     * are drawn at and for the same reason: the request was perfectly well formed, it is the state
     * of the notice that will not allow it, so a page telling somebody to correct what they typed
     * would send them looking for a mistake they did not make.
     *
     * <p>Giving notice on an account that asks for none is a 400 and not a 409. Nothing is in an
     * unexpected state; the account is exactly what it has always been, and the sentence that comes
     * back is good news rather than an obstacle — the money is already theirs, and what they do
     * next is withdraw it.
     */
    @ExceptionHandler(NoticeRefused.class)
    ResponseEntity<ProblemDetail> noticeRefused(NoticeRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NO_SUCH_NOTICE -> HttpStatus.NOT_FOUND;
            case NOT_AN_AMOUNT_OF_MONEY, NOT_A_NOTICE_ACCOUNT -> HttpStatus.BAD_REQUEST;
            case A_NOTICE_NO_LONGER_STANDING -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A fixed term this application will not break.
     *
     * <p>Appended at the end of this class rather than filed beside the other two Products
     * refusals, for the reason the notice handler above it gives: what an administrator may publish
     * about a product, what one customer has given notice on and what one customer may do to the
     * term they are locked into are three subjects that happen to be kept by one module, and a
     * reader following any of them arrives at a handler of its own. It also leaves the switches
     * already in this file untouched, which is what a shared file three siblings are editing
     * deserves.
     *
     * <p>Both kinds are a bad request and neither is a 409, which is worth arguing because the
     * conflict reading is tempting. Nothing is in an unexpected state in either case: an account
     * with no term is exactly what it has always been, and a term that matured did the one thing it
     * was always going to do on the day it was always going to do it. Both sentences are good news
     * — the money is already theirs — and a 409 would dress an answer they are pleased to hear as
     * an obstacle. The same reading, for the same reason, that giving notice on an account with
     * none is given one handler up.
     *
     * <p>A term already broken arrives here as {@code NOT_A_TERM_ACCOUNT} and not as a kind of its
     * own, because breaking moves the account onto free savings: by the time a second press
     * arrives, the account genuinely is not on a term. "A term cannot be broken twice" is a
     * consequence of what breaking does rather than a rule anything checks, and the status follows
     * the fact rather than the history.
     */
    @ExceptionHandler(TermRefused.class)
    ResponseEntity<ProblemDetail> termRefused(TermRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case NOT_A_TERM_ACCOUNT, A_TERM_THAT_HAS_ALREADY_MATURED -> HttpStatus.BAD_REQUEST;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }

    /**
     * A version of the scheme the bank will not publish. Six of the seven are a form to go back and
     * fix, which is what a 400 says; the last is a request that is perfectly well formed and that
     * the versions already announced will not allow, which is what a 409 says.
     *
     * <p><strong>A date that is not a Monday still to come is a 400 and not a 409, which is worth
     * arguing because the conflict reading is tempting.</strong> Nothing about the state of the
     * scheme refuses it: the calendar does. A Wednesday is a Wednesday whatever anybody has
     * published, and a day already gone stays gone — so the thing to do is exactly what a 400 says,
     * go back to the date box and pick a later Monday, and the sentence names the earliest one that
     * would have been accepted. It is the same reading a rate quoted to five places gets one arm
     * up, for the same reason: there is a box, and there is something to type into it.
     *
     * <p><strong>The Monday that would strand an announced version is the one 409.</strong>
     * Everything typed is a perfectly good answer — the day is a Monday, it is still to come, the
     * figures are figures — and what refuses it is a version somebody else has already announced
     * for a later day, which publishing this one would leave permanently out of force. That is the
     * same distinction the duplicate customer and the backdated product version are drawn at:
     * "what you typed is fine, the state of the application will not have it", and it tells the
     * administrator something a 400 would not, which is that there is nothing to correct on the
     * form except in the light of what is already announced.
     *
     * <p>Appended at the end of this class rather than folded into a neighbouring switch, for the
     * reason the two handlers above it give: a shared file that three siblings are editing at once
     * deserves a handler of its own rather than an edit inside one somebody else is holding.
     *
     * <p>A switch over the kind, like every other refusal here: the next reason a version of the
     * scheme can be refused for arrives as a value in {@link SchemeRefused.Kind} and the compiler
     * then asks this method what status it deserves.
     */
    @ExceptionHandler(SchemeRefused.class)
    ResponseEntity<ProblemDetail> schemeRefused(SchemeRefused refusal) {
        HttpStatus status = switch (refusal.kind()) {
            case A_FIGURE_THE_SCHEME_CANNOT_CARRY, A_LADDER_THAT_DESCENDS,
                 RUNGS_THAT_ARE_NOT_A_LADDER, A_VERSION_WITH_NO_DAY_IT_TAKES_EFFECT,
                 A_MONDAY_A_VERSION_CANNOT_TAKE_EFFECT_ON,
                 A_VERSION_THAT_DOES_NOT_SAY_WHAT_CHANGED -> HttpStatus.BAD_REQUEST;
            case A_MONDAY_BEFORE_ONE_ALREADY_ANNOUNCED -> HttpStatus.CONFLICT;
        };
        return ResponseEntity.status(status)
                .body(ProblemDetail.forStatusAndDetail(status, refusal.getMessage()));
    }
}
