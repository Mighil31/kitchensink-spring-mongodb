package io.github.mighil31.kitchensink.member;

import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/** Replaces legacy's JAX-RS {@code MemberResourceRESTService}, at the same {@code /rest/members} path. */
@RestController
@RequestMapping(path = "/rest/members", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberRestController {

    private final MemberRepository repository;
    private final MemberRegistration registration;

    MemberRestController(MemberRepository repository, MemberRegistration registration) {
        this.repository = repository;
        this.registration = registration;
    }

    @GetMapping
    public List<MemberResponse> listAllMembers() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(MemberResponse::from)
                .toList();
    }

    /**
     * 404 for an unknown id and for one that isn't an ObjectId at all ({@code abc}, {@code -1}),
     * as legacy returned 404 for anything outside its {@code [0-9]+} path pattern (ADR 0002). The
     * body is a ProblemDetail ({@link MemberApiExceptionHandler}); legacy's was empty.
     */
    @GetMapping("/{id}")
    public MemberResponse lookupMemberById(@PathVariable String id) {
        if (!ObjectId.isValid(id)) {
            throw new MemberNotFoundException();
        }
        return repository
                .findById(new ObjectId(id))
                .map(MemberResponse::from)
                .orElseThrow(MemberNotFoundException::new);
    }

    /**
     * 201 with {@code Location} and the stored member (legacy: 200, empty body). Invalid body → 400,
     * taken email → 409, both as ProblemDetail ({@link MemberApiExceptionHandler}).
     */
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<MemberResponse> createMember(@Valid @RequestBody MemberRequest request) {
        Member saved = registration.register(request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(saved.id().toHexString())
                .toUri();
        return ResponseEntity.created(location).body(MemberResponse.from(saved));
    }
}
