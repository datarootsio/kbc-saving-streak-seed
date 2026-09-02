package io.dataroots.savingstreak.deposits;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/** Package-private: the rest of the application goes through {@link DepositsService}. */
interface DepositRepository extends JpaRepository<Deposit, Long> {

    List<Deposit> findBySavingsAccountId(long savingsAccountId);

    /**
     * Newest first, and the identifier settles it when two deposits share a moment: a moment is only
     * kept to the millisecond, and two deposits can land inside one.
     */
    List<Deposit> findBySavingsAccountIdOrderByDepositedAtDescIdDesc(long savingsAccountId);

    /**
     * Gives what remains to every deposit that has no answer to the question, and reports how many
     * that was.
     *
     * <p>A deposit recorded before the column existed says nothing about how much of it is left, and
     * all of it is: nothing could have taken any, because nothing could take money out of a savings
     * account when that deposit was made. Run at start-up by {@link DepositsOnStartUp}, which is what
     * lets the money balance be summed from what remains without a balance changing underneath
     * anybody the first time a database written before this is opened.
     *
     * <p>Only the deposits with nothing recorded, so that running it again on a database it has
     * already been through changes nothing — and so that a deposit a withdrawal has drawn down is
     * never handed its money back.
     *
     * <p>It carries its own transaction because its one caller runs before the application has a
     * transaction, a request, or a web server: a statement that writes has to say so itself here.
     */
    @Transactional
    @Modifying
    @Query("update Deposit deposit set deposit.remainingAmount = deposit.amount "
            + "where deposit.remainingAmount is null")
    int giveEveryDepositWhatRemainsOfIt();
}
