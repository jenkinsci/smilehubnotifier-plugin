# SmileHub Notification Plugin for Jenkins

## Usage

### Pipeline

You can use it in the Workflow/Pipeline DSL
```
node {
    try {
     ...
    } catch (e) {
        smilehubSend channel: 'abc', message: 'test'
        throw e
    }
}
```
If you omit channel you can shorten it as it would now use the global default channel:
```
node {
    try {
     ...
    } catch (e) {
        smilehubSend 'test'
        throw e
    }
}
```

The message looks then like this:

![sampel message](smilehub_sample_message.png)

It also works with normal jobs:


![job config](smilhub_job_config.png)

## Admin settings

You can define a default notification channel:


![sampel message](smilhub_admin_settings.png)

# Contribution

## Bugs

If you find a bug in the source code or a mistake in the documentation, you can help us by
submitting an issue to our [JIRA](https://issues.jenkins-ci.org/browse/JENKINS-39690?jql=project%20%3D%20JENKINS%20AND%20component%20%3D%20smilehub-chat-notifier-plugin). Even better you can submit a Pull Request
with a fix.

First search if the issue is already described!

If not create a new issue:

* Tell about your environment:
  * operating system and version
  * Jenkins version
  * Java version
  * SmileHubChat version
* Describe your issue
  * describe your steps leading to the issue
  * attach error logs or screenshots
  * if possible provide test case or screenshots
