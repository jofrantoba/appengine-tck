/*
 * Copyright 2013 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.appengine.tck.mail;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.Set;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import com.google.appengine.api.mail.MailService;
import com.google.appengine.api.mail.MailServiceFactory;
import com.google.appengine.api.utils.SystemProperty;
import com.google.appengine.tck.env.Environment;
import com.google.appengine.tck.mail.support.MimeProperties;
import com.google.appengine.tck.temp.TempDataFilter;
import org.jboss.arquillian.junit.Arquillian;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;


/**
 * Tests sending via {@link MailService#send} and {@link javax.mail.Transport#send}, and, for a
 * deployed application, receiving via a POST.
 * <p/>
 * Set the gateway via the commandline with -Dtck.mail.gateway=your-gateway Emails will look like
 * this: testuser@your-gateway.theappid.appspot.com
 *
 * @author terryok@google.com
 * @author ales.justin@jboss.org
 */
@RunWith(Arquillian.class)
public class MailServiceTest extends MailTestBase {

    private static final String BODY = "Simple message.";

    private static final int TIMEOUT_MAX = 45;

    private MailService mailService;

    @Before
    public void setUp() {
        mailService = MailServiceFactory.getMailService();
        clear();
    }

    @After
    public void tearDown() {
        clear();
    }

    @Test
    public void testSendAndReceiveBasicMessage() throws Exception {
        assumeEnvironment(Environment.APPSPOT, Environment.CAPEDWARF);

        MimeProperties mp = new MimeProperties();
        mp.subject = "Basic-Message-Test-" + System.currentTimeMillis();
        mp.from = getEmail("from-basic-test", EmailMessageField.FROM);
        mp.to = getEmail("to-basic-test", EmailMessageField.TO);
        mp.body = BODY;

        MailService.Message msg = createMailServiceMessage(mp);
        msg.setTextBody(BODY);

        // Send email to self for debugging.
        // msg.setCc("you@yourdomain.com");

        mailService.send(msg);

        assertMessageReceived(mp);
    }

    @Test
    public void testSendAndReceiveFullMessage() throws Exception {
        assumeEnvironment(Environment.APPSPOT, Environment.CAPEDWARF);

        final String textBody = "I am bold.";
        final String htmlBody = "<html><body><b>I am bold.</b></body></html>";

        MimeProperties mp = new MimeProperties();
        mp.subject = "Full-Message-Test-" + System.currentTimeMillis();
        mp.from = getEmail("from-test-full", EmailMessageField.FROM);
        mp.to = getEmail("to-test-full", EmailMessageField.TO);
        mp.cc = getEmail("cc-test-full", EmailMessageField.CC);
        mp.bcc = getEmail("bcc-test-full", EmailMessageField.BCC);
        mp.replyTo = getEmail("replyto-test-full", EmailMessageField.REPLY_TO);

        mp.multiPartsList.add(textBody);
        mp.multiPartsList.add(htmlBody);

        MailService.Message msg = createMailServiceMessage(mp);
        msg.setCc(mp.cc);
        msg.setBcc(mp.bcc);
        msg.setReplyTo(mp.replyTo);
        msg.setTextBody(textBody);
        msg.setHtmlBody(htmlBody);

        mailService.send(msg);

        // Verify that send() did not modify msg.
        assertEquals(mp.subject, msg.getSubject());
        assertEquals(mp.to, msg.getTo().iterator().next());
        assertEquals(mp.from, msg.getSender());
        assertEquals(mp.cc, msg.getCc().iterator().next());
        assertEquals(mp.bcc, msg.getBcc().iterator().next());
        assertEquals(mp.replyTo, msg.getReplyTo());
        assertEquals(textBody, msg.getTextBody());
        assertEquals(htmlBody, msg.getHtmlBody());

        assertMessageReceived(mp);
    }

    @Test
    public void testValidAttachment() throws Exception {
        assumeEnvironment(Environment.APPSPOT, Environment.CAPEDWARF);

        MimeProperties mp = new MimeProperties();
        mp.subject = "Valid-Attachment-Test-" + System.currentTimeMillis();
        mp.from = getEmail("from-test-valid-attachment", EmailMessageField.FROM);
        mp.to = getEmail("to-test-valid-attachment", EmailMessageField.TO);

        MailService.Attachment attachment = createValidAttachment();
        mp.multiPartsList.add(BODY);
        mp.multiPartsList.add(new String(attachment.getData()));

        MailService.Message msg = createMailServiceMessage(mp);
        msg.setTextBody(BODY);
        msg.setAttachments(attachment);

        mailService.send(msg);

        assertMessageReceived(mp);
    }

    @Test
    public void testInvalidAttachment() throws Exception {
        for (String extension : getInvalidAttachmentFileTypes()) {
            MimeProperties mp = new MimeProperties();
            mp.subject = "Invalid-Attachment-Test-" + extension + System.currentTimeMillis();
            mp.from = getEmail("from-test-invalid-attachment", EmailMessageField.FROM);
            mp.to = getEmail("to-test-invalid-attachment", EmailMessageField.TO);

            MailService.Attachment attachment = createInvalidAttachment(extension);
            mp.multiPartsList.add(BODY);
            mp.multiPartsList.add(new String(attachment.getData()));

            MailService.Message msg = createMailServiceMessage(mp);
            msg.setTextBody(BODY);
            msg.setAttachments(attachment);

            try {
                mailService.send(msg);
                throw new IllegalStateException("IllegalArgumentException not thrown for invalid attachment type. " + extension);
            } catch (IllegalArgumentException iae) {
                // as expected
            }
        }
    }

    private List<String> getInvalidAttachmentFileTypes() {
        String[] extensions = {"ade", "adp", "bat", "chm", "cmd", "com", "cpl", "exe",
            "hta", "ins", "isp", "jse", "lib", "mde", "msc", "msp", "mst", "pif", "scr",
            "sct", "shb", "sys", "vb", "vbe", "vbs", "vxd", "wsc", "wsf", "wsh"};
        return Arrays.asList(extensions);
    }

    private MailService.Attachment createValidAttachment() {
        byte[] bytes = "I'm attached to these valid bytes.".getBytes();
        return new MailService.Attachment("test-attach.txt", bytes);
    }

    private MailService.Attachment createInvalidAttachment(String extension) {
        byte[] bytes = "I've got an invalid file type.".getBytes();
        return new MailService.Attachment("test-attach." + extension, bytes);
    }

    @Test
    public void testBounceNotification() throws Exception {
        MimeProperties mp = new MimeProperties();
        mp.subject = "Bounce-Notification-Test-" + System.currentTimeMillis();
        mp.from = getEmail("from-test-bounce", EmailMessageField.FROM);
        mp.to = getEmail("to-test-bounce", EmailMessageField.TO) + "bogus";
        mp.body = BODY;

        MailService.Message msg = createMailServiceMessage(mp);
        msg.setTextBody(BODY);

        mailService.send(msg);

        mp.subject = "BOUNCED:" + mp.subject;  // BounceHandlerServelt also prepends this

        // Assert is not called for this test, see logs to verify BounceHandlerServlet was called.
        // assertMessageReceived(mp);
    }

    @Test
    public void testAllowedHeaders() throws Exception {
        assumeEnvironment(Environment.APPSPOT, Environment.CAPEDWARF);

        MimeProperties mp = new MimeProperties();
        mp.subject = "Allowed-Headers-Test-" + System.currentTimeMillis();
        mp.from = getEmail("from-test-header", EmailMessageField.FROM);
        mp.to = getEmail("to-test-header", EmailMessageField.TO);
        mp.body = BODY;

        MailService.Message msg = createMailServiceMessage(mp);
        msg.setTextBody(BODY);

        // https://developers.google.com/appengine/docs/java/mail/#Sending_Mail_with_Headers
        Set<MailService.Header> headers = new HashSet<>();
        Map<String, String> headersMap = createExpectedHeaders();

        for (Map.Entry entry : headersMap.entrySet()) {
            headers.add(new MailService.Header(entry.getKey().toString(), entry.getValue().toString()));
        }
        msg.setHeaders(headers);
        mailService.send(msg);

        MimeProperties receivedMp = pollForMatchingMail(mp);
        assertHeadersExist(receivedMp, createExpectedHeadersVerifyList(headersMap));
    }

    @Test
    public void testJavaxTransportSendAndReceiveBasicMessage() throws Exception {
        assumeEnvironment(Environment.APPSPOT, Environment.CAPEDWARF);

        Session session = instance(Session.class);
        if (session == null) {
            session = Session.getDefaultInstance(new Properties(), null);
        }
        MimeProperties mp = new MimeProperties();
        mp.subject = "Javax-Transport-Test-" + System.currentTimeMillis();
        mp.from = getEmail("from-test-x", EmailMessageField.FROM);
        mp.to = getEmail("to-test-x", EmailMessageField.TO);
        mp.body = BODY;

        MimeMessage msg = new MimeMessage(session);
        msg.setSubject(mp.subject);
        msg.setFrom(new InternetAddress(mp.from));
        msg.setRecipient(Message.RecipientType.TO, new InternetAddress(mp.to));
        msg.setText(BODY);
        // Send email to self for debugging.
        // msg.setRecipient(Message.RecipientType.CC, new InternetAddress("you@yourdomain.com"));

        Transport.send(msg);

        assertMessageReceived(mp);
    }

    @Test
    public void testSendToAdmin() throws Exception {
        MailService.Message msg = new MailService.Message();
        msg.setSender(getEmail("from-admin-test", EmailMessageField.FROM));
        String subjectTag = "Send-to-admin-" + System.currentTimeMillis();
        msg.setSubject(subjectTag);
        msg.setTextBody(BODY);
        mailService.sendToAdmins(msg);

        // Assuming success if no exception was thrown without calling sendToAdmins();
    }

    @Test
    public void testTextBodyAutomaticallyCreatedFromHtmlBody() throws Exception {
        assumeEnvironment(Environment.APPSPOT, Environment.CAPEDWARF);

        final String textBody = "I am bold.";
        final String htmlBody = "<html><body><b>I am bold.</b></body></html>";

        MimeProperties mp = new MimeProperties();
        mp.subject = "Automatic-Html-To-Text-Conversion-Test-" + System.currentTimeMillis();
        mp.from = getEmail("from-test-htmltext", EmailMessageField.FROM);
        mp.to = getEmail("to-test-htmltext", EmailMessageField.TO);
        mp.multiPartsList.add(textBody);
        mp.multiPartsList.add(htmlBody);

        MailService.Message msg = createMailServiceMessage(mp);
        msg.setHtmlBody(htmlBody);

        mailService.send(msg);

        assertMessageReceived(mp);
    }

    @Ignore("We need to discuss whether to test this or not.")
    @Test
    public void testCanSendEmailWithSenderSetToRegisteredAdminOfTheApp() throws Exception {
        assertSenderAuthorized(getAdminEmail());
    }

    @Ignore("We need to discuss whether to test this or not.")
    @Test
    public void testCanSendEmailWithSenderSetToAnyEmailAddressWithCorrectHostname() throws Exception {
        assertSenderAuthorized("any_user@" + appId() + "." + mailGateway());
    }

    // TODO testCanSendEmailWithSenderSetToCurrentlyLoggedInUser

    @Ignore("We need to discuss whether to test this or not.")
    @Test
    public void testCanNotSendEmailWhenSenderIsUnauthorized() throws Exception {
        assertSenderUnauthorized("someone_else@google.com");
    }

    private String getAdminEmail() {
        String adminTestingAccount = getTestSystemProperty("appengine.adminTestingAccount.email");
        if (adminTestingAccount == null) {
            throw new IllegalStateException("-Dappengine.adminTestingAccount.email is not defined.");
        }
        return adminTestingAccount;
    }

    private void assertSenderAuthorized(String sender) throws IOException {
        MimeProperties mp = new MimeProperties();
        mp.subject = "Test-Authorized-Sender-" + System.currentTimeMillis();
        mp.from = sender;
        mp.to = getEmail("to-authorized-sender-test", EmailMessageField.TO);
        mp.body = BODY;

        MailService.Message msg = createMailServiceMessage(mp);
        msg.setTextBody(BODY);

        try {
            mailService.send(msg);
        } catch (IllegalArgumentException e) {
            if (e.getMessage().contains("Unauthorized Sender")) {
                fail("Could not send mail with sender set to '" + sender + "'. Got exception " + e);
            } else {
                throw e;
            }
        }
    }

    private void assertSenderUnauthorized(String unauthorizedSender) throws IOException {
        MimeProperties mp = new MimeProperties();
        mp.subject = "Test-Unauthorized-Sender-" + System.currentTimeMillis();
        mp.from = unauthorizedSender;
        mp.to = getEmail("to-unauthorized-sender-test", EmailMessageField.TO);
        mp.body = BODY;

        MailService.Message msg = new MailService.Message();
        msg.setSubject(mp.subject);
        msg.setSender(mp.from);
        msg.setTo(mp.to);
        msg.setTextBody(BODY);

        try {
            mailService.send(msg);
            fail("Expected IllegalArgumentException with message \"Unauthorized Sender\"");
        } catch (IllegalArgumentException e) {
            assertTrue("Expected IllegalArgumentException to contain \"Unauthorized Sender\"", e.getMessage().contains("Unauthorized Sender"));
        }
    }

    private void assertMessageReceived(final MimeProperties expectedMimeProps) {
        pollForMatchingMail(expectedMimeProps);
    }

    private MimeProperties pollForMatchingMail(final MimeProperties expectedMimeProps) {
        log.info("Polling for matching mail. Expecting: " + expectedMimeProps);
        MimeProperties mp = pollForMail(new TempDataFilter<MimeProperties>() {
            @Override
            public boolean accept(MimeProperties mp) {
                log.info("Testing for match: " + mp);
                String expectedReplyTo = expectedMimeProps.isReplyToSet() ? expectedMimeProps.replyTo : expectedMimeProps.from;

                if (!Objects.equals(expectedMimeProps.subject, mp.subject)
                    || !Objects.equals(expectedMimeProps.from, mp.from)
                    || !Objects.equals(expectedMimeProps.to, mp.to)
                    || !Objects.equals(expectedMimeProps.cc, mp.cc)
                    || !Objects.equals(expectedReplyTo, mp.replyTo)) {
                    return false;
                }

                if (expectedMimeProps.isBodySet()) {
                    if (!Objects.equals(expectedMimeProps.body, mp.body)) {
                        return false;
                    }
                } else {
                    for (int i = 0; i < expectedMimeProps.multiPartsList.size(); i++) {
                        String expectedPart = expectedMimeProps.multiPartsList.get(i).trim();
                        String actualPart = mp.multiPartsList.get(i).trim();
                        if (!Objects.equals(expectedPart, actualPart)) {
                            return false;
                        }
                    }
                }
                return true;
            }
        });
        if (mp == null) {
            fail("No matching MimeProperties found in temp data after " + TIMEOUT_MAX + " seconds.");
        }
        return mp;
    }

    private void assertHeadersExist(MimeProperties mp, List<String> expectedHeaderLines) {
        List<String> errors = new ArrayList<>();

        for (String headerLine : expectedHeaderLines) {
            if (!mp.headers.contains(headerLine)) {
                errors.add(headerLine + ": was not found.");
            }
        }

        if (!errors.isEmpty()) {
            errors.add("Actual: " + mp.headers);
        }
        assertTrue(errors.toString(), errors.isEmpty());
    }

    /**
     * Allowed headers.
     *
     * @return map of headers to be set and verified.
     */
    private List<String> createExpectedHeadersVerifyList(Map<String, String> map) {
        List<String> headers = new ArrayList<>();

        for (Map.Entry entry : map.entrySet()) {
            headers.add(entry.getKey() + ": " + entry.getValue());
        }
        return headers;
    }

    private Map<String, String> createExpectedHeaders() {
        Map<String, String> headers = new HashMap<>();

        headers.put("In-Reply-To", "123abc");
        headers.put("List-Id", "123abc");
        headers.put("List-Unsubscribe", "123abc");
        headers.put("On-Behalf-Of", "123abc");
        headers.put("References", "123abc");
        headers.put("Resent-Date", "123abc");
        headers.put("Resent-From", "123abc");
        headers.put("Resent-To", "123abc");

        return headers;
    }

    private String getEmail(String user, EmailMessageField field) {
        EmailAddressFormatter emailAddressFormatter = instance(EmailAddressFormatter.class);
        if (emailAddressFormatter == null) {
            return String.format("%s@%s.%s", user, appId(), mailGateway());
        } else {
            return emailAddressFormatter.format(user, appId(), mailGateway(), field);
        }
    }

    private String appId() {
        return SystemProperty.applicationId.get();
    }

    private String mailGateway() {
        String gateway = getTestSystemProperty("tck.mail.gateway", "appspotmail.com");
        log.info("tck.mail.gateway = " + gateway);
        return gateway;
    }

    private MimeProperties pollForMail(TempDataFilter<MimeProperties> filter) {
        return pollForTempData(MimeProperties.class, TIMEOUT_MAX, filter);
    }

    private MailService.Message createMailServiceMessage(MimeProperties mp) {
        MailService.Message msg = new MailService.Message();
        msg.setSubject(mp.subject);
        msg.setSender(mp.from);
        msg.setTo(mp.to);
        return msg;
    }

}
