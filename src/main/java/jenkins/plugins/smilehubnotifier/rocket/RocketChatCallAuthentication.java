package jenkins.plugins.smilehubnotifier.rocket;

import com.mashape.unirest.request.HttpRequest;
import jenkins.plugins.smilehubnotifier.rocket.errorhandling.RocketClientException;

public interface RocketChatCallAuthentication {

  boolean isAuthenticated();

  void doAuthentication() throws RocketClientException;

  String getUrlForRequest(RocketChatRestApiV1 call);

  void addAuthenticationDataToRequest(HttpRequest request);
}
