package jenkins.plugins.smilehubnotifier.workflow;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import com.cloudbees.plugins.credentials.domains.DomainRequirement;
import hudson.AbortException;
import hudson.Extension;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import jenkins.model.Jenkins;
import jenkins.plugins.smilehubnotifier.Messages;
import jenkins.plugins.smilehubnotifier.SmileHubNotifier;
import jenkins.plugins.smilehubnotifier.RocketClient;
import jenkins.plugins.smilehubnotifier.RocketClientImpl;
import jenkins.plugins.smilehubnotifier.RocketClientWebhookImpl;
import jenkins.plugins.smilehubnotifier.model.MessageAttachment;
import jenkins.plugins.smilehubnotifier.rocket.errorhandling.RocketClientException;
import org.apache.commons.lang.StringUtils;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.jenkinsci.plugins.workflow.steps.AbstractStepDescriptorImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractStepImpl;
import org.jenkinsci.plugins.workflow.steps.AbstractSynchronousNonBlockingStepExecution;
import org.jenkinsci.plugins.workflow.steps.StepContextParameter;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

import javax.annotation.Nonnull;
import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.cloudbees.plugins.credentials.CredentialsProvider.lookupCredentials;

/**
 * Workflow step to send a rocket channel notification.
 */
public class RocketSendStep extends AbstractStepImpl {

  private static final Logger LOG = Logger.getLogger(RocketSendStep.class.getName());

  @Nonnull
  private final String message;
  private String serverUrl;
  private boolean trustSSL;
  private String channel;
  private boolean failOnError;
  private String webhookToken;
  private String webhookTokenCredentialId;

  private String emoji;
  private String avatar;
  private String color;
  private boolean rawMessage;
  private List<MessageAttachment> attachments;

  @Nonnull
  public String getMessage() {
    return message;
  }

  public String getChannel() {
    return channel;
  }

  public String getServerUrl() {
    return serverUrl;
  }

  public boolean isTrustSSL() {
    return trustSSL;
  }

  public String getEmoji() {
    return emoji;
  }

  public String getAvatar() {
    return avatar;
  }

  public String getColor() {
    return color;
  }

  public boolean isRawMessage() {
    return rawMessage;
  }

  public String getWebhookToken() {
    return webhookToken;
  }

  public String getWebhookTokenCredentialId() {
    return webhookTokenCredentialId;
  }

  public List<MessageAttachment> getAttachments() {
    return attachments;
  }

  @DataBoundSetter
  public void setEmoji(final String emoji) {
    this.emoji = Util.fixEmpty(emoji);
  }

  @DataBoundSetter
  public void setAvatar(final String avatar) {
    this.avatar = Util.fixEmpty(avatar);
  }

  @DataBoundSetter
  public void setColor(final String color) {
    this.color = Util.fixEmpty(color);
  }

  @DataBoundSetter
  public void setAttachments(final List<MessageAttachment> attachments) {
    this.attachments = attachments;
  }

  @DataBoundSetter
  public void setChannel(String channel) {
    this.channel = Util.fixEmpty(channel);
  }

  @DataBoundSetter
  public void setServerUrl(String serverUrl) {
    this.serverUrl = Util.fixEmpty(serverUrl);
  }

  @DataBoundSetter
  public void setTrustSSL(final boolean trustSSL) {
    this.trustSSL = trustSSL;
  }

  public boolean isFailOnError() {
    return failOnError;
  }

  @DataBoundSetter
  public void setFailOnError(boolean failOnError) {
    this.failOnError = failOnError;
  }

  @DataBoundSetter
  public void setRawMessage(final boolean rawMessage) {
    this.rawMessage = rawMessage;
  }

  @DataBoundSetter
  public void setWebhookToken(final String webhookToken) {
    this.webhookToken = Util.fixEmpty(webhookToken);
  }

  @DataBoundSetter
  public void setWebhookTokenCredentialId(final String webhookTokenCredentialId) {
    this.webhookTokenCredentialId = Util.fixEmpty(webhookTokenCredentialId);
  }

  @DataBoundConstructor
  public RocketSendStep(@Nonnull String message) {
    this.message = message;
  }

  @Extension
  public static class DescriptorImpl extends AbstractStepDescriptorImpl {

    public DescriptorImpl() {
      super(RocketSendStepExecution.class);
    }

    @Override
    public String getFunctionName() {
      return "smilehubSend";
    }

    @Override
    public String getDisplayName() {
      return Messages.RocketSendStepDisplayName();
    }

    public ListBoxModel doFillWebhookTokenCredentialIdItems() {
      if (!Jenkins.getInstance().hasPermission(Jenkins.ADMINISTER)) {
        return new ListBoxModel();
      }
      return new StandardListBoxModel()
        .withEmptySelection()
        .withAll(lookupCredentials(
          StringCredentials.class,
          Jenkins.getInstance(),
          ACL.SYSTEM,
          Collections.<DomainRequirement>emptyList())
        );
    }

    //WARN users that they should not use the plain/exposed token, but rather the token credential id
    public FormValidation doCheckWebhookToken(@QueryParameter String value) {
      Jenkins.get().checkPermission(Jenkins.ADMINISTER);
      if (Util.fixEmptyAndTrim(value) == null) {
        return FormValidation.ok();
      }
      return FormValidation.warning("Exposing your Integration Token is a security risk. Please use the Webhook Token Credential ID");
    }
  }

  public static class RocketSendStepExecution extends AbstractSynchronousNonBlockingStepExecution<Void> {

    private static final long serialVersionUID = 1L;

    @Inject
    transient RocketSendStep step;

    @StepContextParameter
    transient TaskListener listener;

    @StepContextParameter
    transient Run run;

    @Override
    protected Void run() throws Exception {
      Thread.setDefaultUncaughtExceptionHandler(new Thread.UncaughtExceptionHandler() {
        public void uncaughtException(Thread t, Throwable e) {
          LOG.log(Level.SEVERE, t + " runStep threw an exception: ", e);
        }
      });

      //default to global config values if not set in step, but allow step to override all global settings
      Jenkins jenkins;
      //Jenkins.getInstance() may return null, no message sent in that case
      try {
        jenkins = Jenkins.getInstance();
      }
      catch (NullPointerException ne) {
        listener.error(Messages.NotificationFailedWithException(ne));
        return null;
      }
      SmileHubNotifier.DescriptorImpl rocketDesc = jenkins.getDescriptorByType(
        SmileHubNotifier.DescriptorImpl.class);
      String server = step.serverUrl != null ? step.serverUrl : rocketDesc.getRocketServerUrl();
      server = server != null ? server : "";
      
      boolean trustSSL = step.trustSSL || rocketDesc.isTrustSSL();
      String user = rocketDesc.getUsername();
      String password = Secret.toString(rocketDesc.getPassword());
      String channel = step.channel != null ? step.channel : rocketDesc.getChannel();
      String jenkinsUrl = rocketDesc.getBuildServerUrl() != null ? rocketDesc.getBuildServerUrl() : "";
      String webhookToken = step.webhookToken != null ? step.webhookToken : Secret.toString(rocketDesc.getWebhookToken());
      String webhookTokenCredentialId = step.webhookTokenCredentialId != null ? step.webhookTokenCredentialId : rocketDesc.getWebhookTokenCredentialId();
      // placing in console log to simplify testing of retrieving values from global config or from step field; also used for tests
      listener.getLogger().println(Messages.RocketSendStepConfig(server, trustSSL, channel, step.message));

      // getRocketClient needs to be wrapped inside a try-catch because it can fail too if the target RocketChat server does not behave properly.
      try {
        RocketClient rocketClient = getRocketClient(server, trustSSL, user, password, channel, webhookToken, webhookTokenCredentialId);

        String msg = step.message;
        if (!step.rawMessage) {
          msg += ", " + run.getFullDisplayName().replace(" #", " No.") + ", " + run.getAbsoluteUrl() + "";
        }

        boolean publishSuccess = rocketClient.publish(msg, step.emoji, step.avatar,
          MessageAttachment.convertMessageAttachmentsToMaps(step.attachments));
        if (!publishSuccess && step.failOnError) {
          throw new AbortException(Messages.NotificationFailed());
        }
        else if (!publishSuccess) {
          listener.error(Messages.NotificationFailed());
        }
        return null;

      } catch (RocketClientException rce) {
        if (step.failOnError) {
          throw rce;
        } else {
          listener.error(Messages.NotificationFailedWithException(rce));
          return null;
        }
      }
    }

    //streamline unit testing
    RocketClient getRocketClient(String server, boolean trustSSL, String user, String password, String channel,
                                 String webhookToken, String webhookTokenCredentialId) throws RocketClientException {
      if (!StringUtils.isEmpty(webhookToken) || !StringUtils.isEmpty(webhookTokenCredentialId)) {
        return new RocketClientWebhookImpl(server, trustSSL, webhookToken, webhookTokenCredentialId, channel);
      }
      return new RocketClientImpl(server, trustSSL, user, password, channel);
    }

  }
}
