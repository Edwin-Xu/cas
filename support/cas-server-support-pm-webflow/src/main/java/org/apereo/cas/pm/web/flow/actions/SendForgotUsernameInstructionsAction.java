package org.apereo.cas.pm.web.flow.actions;

import org.apereo.cas.configuration.CasConfigurationProperties;
import org.apereo.cas.notifications.CommunicationsManager;
import org.apereo.cas.notifications.mail.EmailMessageBodyBuilder;
import org.apereo.cas.pm.PasswordManagementService;
import org.apereo.cas.web.flow.CasWebflowConstants;
import org.apereo.cas.web.support.WebUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.EmailValidator;
import org.apereo.inspektr.audit.annotation.Audit;
import org.springframework.binding.message.MessageBuilder;
import org.springframework.webflow.action.AbstractAction;
import org.springframework.webflow.action.EventFactorySupport;
import org.springframework.webflow.execution.Event;
import org.springframework.webflow.execution.RequestContext;

import java.util.Map;

/**
 * This is {@link SendForgotUsernameInstructionsAction}.
 *
 * @author Misagh Moayyed
 * @since 6.0.0
 */
@Slf4j
@RequiredArgsConstructor
public class SendForgotUsernameInstructionsAction extends AbstractAction {

    /**
     * Parameter name to look up the user.
     */
    public static final String REQUEST_PARAMETER_EMAIL = "email";

    /**
     * The CAS configuration properties.
     */
    protected final CasConfigurationProperties casProperties;

    /**
     * The communication manager for SMS/emails.
     */
    protected final CommunicationsManager communicationsManager;

    /**
     * The password management service.
     */
    protected final PasswordManagementService passwordManagementService;

    @Audit(action = "REQUEST_FORGOT_USERNAME",
        principalResolverName = "REQUEST_FORGOT_USERNAME_PRINCIPAL_RESOLVER",
        actionResolverName = "REQUEST_FORGOT_USERNAME_ACTION_RESOLVER",
        resourceResolverName = "REQUEST_FORGOT_USERNAME_RESOURCE_RESOLVER")
    @Override
    protected Event doExecute(final RequestContext requestContext) {
        communicationsManager.validate();
        if (!communicationsManager.isMailSenderDefined()) {
            return getErrorEvent("email.failed", "Unable to send email as no mail sender is defined", requestContext);
        }
        val request = WebUtils.getHttpServletRequestFromExternalWebflowContext(requestContext);
        val email = request.getParameter(REQUEST_PARAMETER_EMAIL);

        if (StringUtils.isBlank(email)) {
            return getErrorEvent("email.required", "No email is provided", requestContext);
        }

        if (!EmailValidator.getInstance().isValid(email)) {
            return getErrorEvent("email.invalid", "Provided email address is invalid", requestContext);
        }

        val username = passwordManagementService.findUsername(email);
        if (StringUtils.isBlank(username)) {
            return getErrorEvent("username.missing", "No username could be located for the given email address", requestContext);
        }

        if (sendForgotUsernameEmailToAccount(email, username)) {
            return success();
        }

        return getErrorEvent("username.failed", "Failed to send the username to the given email address", requestContext);
    }

    /**
     * Send forgot username email to account.
     *
     * @param email    the email
     * @param username the username
     * @return the boolean
     */
    protected boolean sendForgotUsernameEmailToAccount(final String email, final String username) {
        val reset = casProperties.getAuthn().getPm().getForgotUsername().getMail();
        val body = EmailMessageBodyBuilder.builder().properties(reset)
            .parameters(Map.of("email", email)).build().produce();
        return this.communicationsManager.email(reset, email, body);
    }

    private Event getErrorEvent(final String code, final String defaultMessage, final RequestContext requestContext) {
        val messages = requestContext.getMessageContext();
        messages.addMessage(new MessageBuilder()
            .error()
            .code("screen.pm.forgotusername." + code)
            .build());
        LOGGER.error(defaultMessage);
        return new EventFactorySupport().event(this, CasWebflowConstants.VIEW_ID_ERROR);
    }
}
