package io.dataroots.savingstreak.accounts;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Package-private on purpose: how the Accounts module stores customers is nobody else's business.
 * The rest of the application goes through {@link AccountsService}.
 */
interface CustomerRepository extends JpaRepository<Customer, Long> {

    /**
     * The customer who gave this address, whatever case they typed it in. Somebody signing in types
     * their own address from memory, and a capital letter is not a different person.
     */
    Optional<Customer> findByContactDetailsIgnoreCase(String contactDetails);
}
