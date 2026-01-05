package com.reapro.achat.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

@Service
@RequiredArgsConstructor
public class EmailService {

    private final JavaMailSender mailSender;
    private final SpringTemplateEngine templateEngine;

    private static final String FROM = "notify@agence3s.tn";

    private void sendHtmlEmail(String to, String subject, String template, Context ctx)
            throws MessagingException {

        MimeMessage message = mailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

        String html = templateEngine.process(template, ctx);

        helper.setFrom(FROM);
        helper.setTo(to);
        helper.setSubject(subject);
        helper.setText(html, true);

        mailSender.send(message);
    }

    // 1️⃣ Email de vérification d’inscription
    public void sendVerificationCodeHtml(String to, String name, String code)
            throws MessagingException {

        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("code", code);

        sendHtmlEmail(
                to,
                "Votre code de vérification",
                "email-verification",
                ctx
        );
    }

    // 2️⃣ Mot de passe temporaire (reset)
    public void sendTempPasswordHtml(String to, String name, String tempPassword)
            throws MessagingException {

        Context ctx = new Context();
        ctx.setVariable("name", name);
        ctx.setVariable("password", tempPassword);

        sendHtmlEmail(
                to,
                "Votre mot de passe temporaire",
                "email-temp-password",
                ctx
        );
    }

    // 3️⃣ Confirmation de changement de mot de passe
    public void sendPasswordChangedHtml(String to, String name)
            throws MessagingException {

        Context ctx = new Context();
        ctx.setVariable("name", name);

        sendHtmlEmail(
                to,
                "Mot de passe modifié",
                "email-password-changed",
                ctx
        );
    }

    // 4️⃣ Confirmation d’activation de compte (optionnel)
    public void sendAccountCreatedHtml(String to, String name)
            throws MessagingException {

        Context ctx = new Context();
        ctx.setVariable("name", name);

        sendHtmlEmail(
                to,
                "Compte activé",
                "email-account-created",
                ctx
        );
    }
}
