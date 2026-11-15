package io.github.mighil31.kitchensink.member;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import org.bson.types.ObjectId;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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

    /**
     * Every member, as legacy, unless the client asks for a page (gap A3, ADR 0013): {@code ?page=}
     * (from 0) and/or {@code ?size=} (1 to {@value #MAX_PAGE_SIZE}, default {@value
     * #DEFAULT_PAGE_SIZE}). The body is a plain array either way, so the response shape never
     * changes; when there are more members, a {@code Link: <...>; rel="next"} header points to the
     * next page (RFC 8288). Out-of-range values are a 400 ProblemDetail.
     */
    @GetMapping
    public ResponseEntity<List<MemberResponse>> listMembers(
            @RequestParam(required = false) @Min(0) Integer page,
            @RequestParam(required = false) @Min(1) @Max(MAX_PAGE_SIZE) Integer size) {
        if (page == null && size == null) {
            return ResponseEntity.ok(repository.findAllByOrderByNameAscIdAsc().stream()
                    .map(MemberResponse::from)
                    .toList());
        }
        int pageNumber = page == null ? 0 : page;
        int pageSize = size == null ? DEFAULT_PAGE_SIZE : size;
        Slice<Member> slice = repository.findAllByOrderByNameAscIdAsc(PageRequest.of(pageNumber, pageSize));
        ResponseEntity.BodyBuilder response = ResponseEntity.ok();
        if (slice.hasNext()) {
            String next = ServletUriComponentsBuilder.fromCurrentRequest()
                    .replaceQueryParam("page", pageNumber + 1)
                    .replaceQueryParam("size", pageSize)
                    .toUriString();
            response.header(HttpHeaders.LINK, "<" + next + ">; rel=\"next\"");
        }
        return response.body(slice.map(MemberResponse::from).getContent());
    }

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

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
