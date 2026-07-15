package com.bradandmarsha.acruet.mail;

import com.bradandmarsha.acruet.config.SmtpSettings;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Sends transactional mail via Proton SMTP submission (STARTTLS).
 */
public final class MailSender {

    private static final Logger LOGGER = Logger.getLogger(MailSender.class.getName());

    private final SmtpSettings settings;

    public MailSender(SmtpSettings settings) {
        this.settings = settings;
    }

    public void send(String to, String subject, String body) throws MessagingException {
        if (!settings.isConfigured()) {
            throw new MessagingException("SMTP is not configured");
        }
        Session session = Session.getInstance(smtpProperties());
        MimeMessage message = new MimeMessage(session);
        message.setFrom(new InternetAddress(settings.fromAddress()));
        message.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to, false));
        message.setSubject(subject, "UTF-8");
        message.setText(body, "UTF-8");
        Transport transport = session.getTransport("smtp");
        try {
            transport.connect(settings.host(), settings.port(), settings.username(), settings.password());
            transport.sendMessage(message, message.getAllRecipients());
        } catch (MessagingException exception) {
            LOGGER.log(Level.WARNING, "SMTP send failed for recipient {0}", to);
            LOGGER.log(Level.FINE, "SMTP failure detail", exception);
            throw exception;
        } finally {
            try {
                transport.close();
            } catch (MessagingException exception) {
                LOGGER.log(Level.FINE, "SMTP transport close failed", exception);
            }
        }
    }

    private Properties smtpProperties() {
        Properties properties = new Properties();
        properties.put("mail.smtp.host", settings.host());
        properties.put("mail.smtp.port", String.valueOf(settings.port()));
        properties.put("mail.smtp.auth", "true");
        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.starttls.required", "true");
        properties.put("mail.smtp.ssl.protocols", "TLSv1.2");
        properties.put("mail.smtp.ssl.trust", settings.host());
        return properties;
    }
}
