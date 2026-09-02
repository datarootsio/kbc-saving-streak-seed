package io.dataroots.savingstreak.web;

import io.dataroots.savingstreak.clock.ClockService;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The application's clock: where it is standing, and a way to move it forward.
 *
 * <p>A lab affordance rather than part of the product, and it exists in the development profile
 * alone. Without that profile this controller is not built, so the paths below are not routes and an
 * application in front of a customer has no time machine in it to find. Nothing in the frontend calls
 * either of them; it is a trainer's or a participant's tool, reached with curl.
 *
 * <p>Under {@code /api/dev} so that what is a demonstration aid and what is the application is
 * legible from the path alone.
 */
@RestController
@RequestMapping("/api/dev/clock")
@Profile("dev")
class DevelopmentClockController {

    private final ClockService clock;

    DevelopmentClockController(ClockService clock) {
        this.clock = clock;
    }

    /** Where in time the application is, for somebody who has lost track mid-exercise. */
    @GetMapping
    ClockResponse where() {
        return ClockResponse.of(clock.howFarItHasMoved());
    }

    @PostMapping("/advance")
    ClockResponse advance(@RequestBody AdvanceClockRequest request) {
        // Reading the request, not judging it. Whether the clock will make that move — forwards, and
        // not further than it goes — is a rule, and it belongs to the clock, which refuses on its own.
        if (request == null || request.days() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Moving the clock needs a number of days to move it by.");
        }
        return ClockResponse.of(clock.advanceBy(request.days()));
    }
}
