package io.github.mighil31.kitchensink.member;

import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Replaces legacy's JAX-RS {@code MemberResourceRESTService}, at the same {@code /rest/members} path. */
@RestController
@RequestMapping(path = "/rest/members", produces = MediaType.APPLICATION_JSON_VALUE)
public class MemberRestController {

    private final MemberRepository repository;

    MemberRestController(MemberRepository repository) {
        this.repository = repository;
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
     * empty body matches legacy until errors move to ProblemDetail (ADR 0003).
     */
    @GetMapping("/{id}")
    public ResponseEntity<MemberResponse> lookupMemberById(@PathVariable String id) {
        if (!ObjectId.isValid(id)) {
            return ResponseEntity.notFound().build();
        }
        return repository
                .findById(new ObjectId(id))
                .map(MemberResponse::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
