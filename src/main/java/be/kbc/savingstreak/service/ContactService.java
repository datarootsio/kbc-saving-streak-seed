package be.kbc.savingstreak.service;

import be.kbc.savingstreak.domain.Member;
import be.kbc.savingstreak.repo.MemberRepository;
import be.kbc.savingstreak.web.dto.ContactRequest;
import be.kbc.savingstreak.web.dto.ContactResult;
import be.kbc.savingstreak.web.dto.ContactView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The people the signed-in customer can send points to. */
@Service
public class ContactService {

    private final MemberRepository members;
    private final OverviewService overviewService;

    public ContactService(MemberRepository members, OverviewService overviewService) {
        this.members = members;
        this.overviewService = overviewService;
    }

    @Transactional
    public ContactResult add(ContactRequest request) {
        String firstName = clean(request.firstName());
        String lastName = clean(request.lastName());
        if (firstName.isEmpty() || lastName.isEmpty()) {
            throw new BusinessRuleException("Fill in both a first and a last name.");
        }

        Member contact = members.save(new Member(firstName, lastName));
        return new ContactResult(
                new ContactView(contact.getId(), contact.fullName(), contact.initials()),
                overviewService.overview());
    }

    /** Trims the name and collapses runs of whitespace, so initials stay predictable. */
    private static String clean(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ");
    }
}
