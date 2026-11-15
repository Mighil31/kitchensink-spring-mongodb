package io.github.mighil31.kitchensink.member;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The registration page: replaces legacy's JSF {@code index.xhtml}, its {@code @Model
 * MemberController} and the {@code MemberListProducer} (ADR 0004).
 *
 * <p>Post/Redirect/Get: a successful POST redirects, so refreshing doesn't register twice (gap
 * B14), and the GET loads the list, which is why legacy's CDI event isn't needed (ADR 0012).
 * Errors are field messages, never exception text (gap B7).
 */
@Controller
class MemberController {

    private final MemberRepository repository;
    private final MemberRegistration registration;

    MemberController(MemberRepository repository, MemberRegistration registration) {
        this.repository = repository;
        this.registration = registration;
    }

    @GetMapping("/")
    String index(Model model) {
        if (!model.containsAttribute("newMember")) {
            model.addAttribute("newMember", new MemberRequest(null, null, null));
        }
        model.addAttribute("members", members());
        return "index";
    }

    @PostMapping("/")
    String register(
            @Valid @ModelAttribute("newMember") MemberRequest newMember,
            BindingResult result,
            Model model,
            RedirectAttributes redirect) {
        if (!result.hasErrors()) {
            try {
                registration.register(newMember);
                redirect.addFlashAttribute("registered", true);
                return "redirect:/";
            } catch (EmailAlreadyRegisteredException e) {
                result.rejectValue("email", "emailTaken", "Email taken");
            }
        }
        model.addAttribute("members", members());
        return "index";
    }

    private List<MemberResponse> members() {
        return repository.findAllByOrderByNameAsc().stream()
                .map(MemberResponse::from)
                .toList();
    }
}
