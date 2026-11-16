package io.github.mighil31.kitchensink.member;

import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The registration page. Post/Redirect/Get: a successful POST redirects, so a refresh doesn't
 * register twice, and the GET reloads the list (ADR 0012).
 */
@Controller
class MemberController {

    private final MemberRepository repository;
    private final MemberRegistration registration;
    private final MessageSource messages;

    MemberController(MemberRepository repository, MemberRegistration registration, MessageSource messages) {
        this.repository = repository;
        this.registration = registration;
        this.messages = messages;
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
        Locale locale = LocaleContextHolder.getLocale();
        model.addAttribute(
                "fieldMessages",
                FieldMessages.firstPerField(result.getFieldErrors(), error -> messages.getMessage(error, locale)));
        model.addAttribute("members", members());
        return "index";
    }

    private List<MemberResponse> members() {
        return repository.findAllByOrderByNameAscIdAsc().stream()
                .map(MemberResponse::from)
                .toList();
    }
}
