package org.comroid.auth.controller;

import org.comroid.auth.entity.UserAccount;
import org.comroid.auth.repo.AccountRepository;
import org.comroid.auth.web.WebPagePreparator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.util.UUID;
import java.util.stream.StreamSupport;

@Controller
@RequestMapping("/account")
public class AccountController {
    @Autowired
    private AccountRepository accounts;
    @Autowired
    private JavaMailSender mailSender;

    @GetMapping
    public String index(HttpSession session) {
        if (session == null)
            return "redirect:/login";
        var account = accounts.findBySessionId(session.getId());
        if (account.isEmpty())
            return "redirect:/login";
        return "redirect:/account/" + account.get().getId();
    }

    @GetMapping("/list")
    public String list(Model model, HttpSession session) {
        if (session == null)
            return "redirect:/login";
        var account = accounts.findBySessionId(session.getId());
        if (account.isEmpty() || !account.get().hasPermission(UserAccount.Permit.AdminAccounts))
            return new WebPagePreparator(model, "generic/unauthorized")
                    .userAccount(account.orElse(null))
                    .complete();
        return new WebPagePreparator(model, "account/list")
                .userAccount(account.get())
                .userAccountList(StreamSupport.stream(accounts.findAll().spliterator(), false).toList())
                .complete();
    }

    @GetMapping("/{id}")
    public String view(Model model, @PathVariable("id") String id) {
        var account = accounts.findById(id);
        if (account.isEmpty())
            return "redirect:/login";
        return new WebPagePreparator(model, "account/view")
                .userAccount(account.get())
                .complete();
    }

    @GetMapping("/edit")
    public String edit(Model model, HttpSession session) {
        if (session == null)
            return "redirect:/login";
        var account = accounts.findBySessionId(session.getId());
        if (account.isEmpty())
            return "redirect:/login";
        return new WebPagePreparator(model, "account/edit")
                .userAccount(account.get())
                .setAttribute("editing", account.get())
                .setAttribute("self", true)
                .complete();
    }

    @PostMapping(value = "/edit", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String edit(
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            HttpSession session
    ) {
        if (session == null)
            return "redirect:/login";
        var account = accounts.findBySessionId(session.getId());
        if (account.isEmpty())
            return "redirect:/login";
        var found = account.get();
        found.setUsername(username);
        var prev = found.getEmail();
        if (!prev.equals(email))
            initiateEmailVerification(found);
        found.setEmail(email);
        accounts.save(found);
        return "redirect:/account";
    }

    @GetMapping("/{id}/edit")
    public String edit(Model model, @PathVariable("id") String id, HttpSession session) {
        if (session == null)
            return "redirect:/login";
        var account = accounts.findBySessionId(session.getId());
        var editing = accounts.findById(id);
        if (account.isEmpty() || editing.isEmpty() || !account.get().hasPermission(UserAccount.Permit.AdminAccounts))
            return new WebPagePreparator(model, "generic/unauthorized")
                    .userAccount(account.orElse(null))
                    .complete();
        return new WebPagePreparator(model, "account/edit")
                .userAccount(account.get())
                .setAttribute("editing", editing.get())
                .setAttribute("self", false)
                .complete();
    }

    @PostMapping(value = "/{id}/edit", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public String edit(
            Model model,
            @PathVariable("id") String id,
            @RequestParam("username") String username,
            @RequestParam("email") String email,
            @RequestParam("permit") int permit,
            @RequestParam(value = "enabled", required = false, defaultValue = "false") boolean enabled,
            @RequestParam(value = "locked", required = false, defaultValue = "false") boolean locked,
            @RequestParam(value = "expired", required = false, defaultValue = "false") boolean expired,
            @RequestParam(value = "credentialsExpired", required = false, defaultValue = "false") boolean credentialsExpired,
            HttpSession session
    ) {
        if (session == null)
            return "redirect:/login";
        var account = accounts.findBySessionId(session.getId());
        var editing = accounts.findById(id);
        if (account.isEmpty() || editing.isEmpty())
            return "redirect:/login";
        if (!account.get().hasPermission(UserAccount.Permit.AdminAccounts))
            return new WebPagePreparator(model, "generic/unauthorized")
                    .userAccount(account.orElse(null))
                    .complete();
        var found = editing.get();
        found.setUsername(username);
        found.setEmail(email);
        found.setPermit(permit);
        found.setEnabled(enabled);
        found.setLocked(locked);
        found.setExpired(expired);
        found.setCredentialsExpired(credentialsExpired);
        accounts.save(found);
        return "redirect:/account/list";
    }

    @GetMapping("/change_password")
    public String changePassword(Model model, HttpSession session) {
        if (session == null)
            return "redirect:/login";
        var account = accounts.findBySessionId(session.getId());
        if (account.isEmpty())
            return "redirect:/login";
        return new WebPagePreparator(model, "account/change_password")
                .userAccount(account.get())
                .complete();
    }

    @GetMapping("/email_verification")
    public String initiateEmailVerification(Model model, HttpSession session) {
        if (session == null)
            return "redirect:/login";
        var account = accounts.findBySessionId(session.getId());
        if (account.isEmpty())
            return "redirect:/login";
        initiateEmailVerification(account.get());
        return "redirect:/account";
    }

    @GetMapping("/verify_email")
    public String verifyEmail(Model model, @RequestParam("code") String code) {
        var account = accounts.findByEmailVerificationCode(code);
        if (account.isEmpty() || account.get().isEmailVerified())
            return new WebPagePreparator(model, "generic/unauthorized")
                    .userAccount(account.orElse(null))
                    .complete();
        var found = account.get();
        found.setEmailVerified(true);
        found.setEmailVerifyCode(null);
        accounts.save(found);
        return "redirect:/account";
    }

    private void initiateEmailVerification(UserAccount account) {
        String code;
        do {
            code = UUID.randomUUID().toString();
        } while (accounts.findByEmailVerificationCode(code).isPresent());
        account.setEmailVerified(false);
        account.setEmailVerifyCode(code);
        accounts.save(account);
        var mail = new SimpleMailMessage();
        mail.setFrom("noreply@comroid.org");
        mail.setTo(account.getEmail());
        mail.setSubject("comroid Account E-Mail Verification");
        mail.setText(String.format("""
                Hello,
                                        
                This is an automated E-Mail asking you to verify the email for your comroid Account.
                                        
                Please click on the following link to verify your email:
                https://auth.comroid.org/verify_email?code=%s
                Do not reply to this email.
                                        
                Kind regards,
                comroid Team
                """, code));
        mailSender.send(mail);
    }
}
