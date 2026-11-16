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
     * Every member, unless {@code ?page=} or {@code ?size=} asks for a page (ADR 0013). The body is
     * a plain array either way; a {@code Link: <...>; rel="next"} header points to the next page.
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

    /** An id that isn't an ObjectId ({@code abc}) is a 404 too, not a 400, as on legacy (ADR 0002). */
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
