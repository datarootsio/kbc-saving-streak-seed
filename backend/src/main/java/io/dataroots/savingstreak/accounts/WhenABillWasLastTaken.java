package io.dataroots.savingstreak.accounts;

import java.time.LocalDate;

/**
 * The most recent date one bill's money actually left the account.
 *
 * <p>Two columns out of an aggregate rather than a whole row, because that is the whole of what a
 * page drawing a list of bills asks: every bill on the account, and the last day each of them was
 * taken. Asked for every bill at once — see
 * {@code BillOccurrenceRepository.whenEachOfTheseWasLastTaken} — so that drawing twenty bills is one
 * query rather than twenty.
 *
 * <p>The date a bill was <em>paid on</em> and not the date it was presented. A date that was
 * presented and refused is not a date the rent was taken, and a card saying "last taken on the 1st"
 * about a month the money never left would be the page telling the customer the opposite of what
 * happened.
 *
 * <p>Package-private, like everything else in this module that is not a record leaving it: it is a
 * shape for one query's answer, and what leaves is the {@code lastTakenOn} already folded into
 * {@link ADeclaredBill}.
 */
record WhenABillWasLastTaken(long billId, LocalDate lastTakenOn) {
}
