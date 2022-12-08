# Configuration

You can customize the behavior of the Just-In-Time Access application by editing the `env_variables` section of the
[`app.yaml` configuration file](https://cloud.google.com/appengine/docs/standard/java-gen2/config/appref) and
redeploying the application.

The Just-In-Time Access application supports the following environment variables:

<table>
  <tr>
    <th>Name</th>
    <th>Description</th>
    <th>Required</th>
    <th>Default</th>
    <th>Available since</th>
  </tr>
  <tr>
    <td colspan="4"><b>Basic configuration</b></td>
  </tr>  <tr>
    <td>
        <code>RESOURCE_SCOPE</code>
    </td>
    <td>
        The part of the Google Cloud resource hierarchy that's managed by using this application.
        You can use one of the following values:
        <ul>
            <li><code>organizations/ORGANIZATION_ID</code> (all projects)</li>
            <li><code>folders/FOLDER_ID</code> (projects underneath a specific project)</li>
            <li><code>projects/PROJECT_ID</code> (specific project)</li>
        </ul>
        <p>
            For ORGANIZATION_ID, FOLDER_ID, or PROJECT_ID, use the ID of the organization, folder, or project that you're using
            the application with.
        </p>
        <p>
            You must grant the application's service account access to the appropriate node of the resource hierarchy.
        </p>
    </td>
    <td>Required</td>
    <td>Project in which Just-In-Time Access application is deployed</td>
    <td>1.0</td>
  </tr>
  <tr>
    <td>
        <code>ACTIVATION_TIMEOUT</code>,
        <code>ELEVATION_DURATION</code>
    </td>
    <td>
        <p>Duration (in minutes) for which a role remains activated.<br/><br/>
        The timeout is relative to the time when the user requested access.</p> 
    </td>
    <td>Required</td>
    <td><code>120</code></td>
    <td>1.0</td>
  </tr>
  <tr>
    <td>
        <code>JUSTIFICATION_HINT</code>
    </td>
    <td>
        <p>Hint that indicates which kind of justification users are expected to provide.</p>
    </td>
    <td>Required</td>
    <td><code>Bug or case number</code></td>
    <td>1.0</td>
  </tr>
  <tr>
    <td>
        <code>JUSTIFICATION_PATTERN</code>
    </td>
    <td>
        <p>A regular expression that a justification has to match. </p>
        <p>
            For example, if you expect users to provide a ticket number in the form of <code>CASE-123</code> as
            justification, you can use the expression <code>^CASE-\d+$</code> to enforce this convention.
        </p>
    </td>
    <td>Required</td>
    <td><code>.*</code></td>
    <td>1.0</td>
  </tr>
  <tr>
    <td colspan="4"><b>Multi-party approval</b> (MPA)</td>
  </tr>
  <tr>
    <td>
        <code>ACTIVATION_REQUEST_TIMEOUT</code>
    </td>
    <td>
        <p>Duration (in minutes) for which an activation request remains valid.</p>
        <p>
            Like <code>ACTIVATION_TIMEOUT</code>, the timeout is relative to the time when the user
            requested access. <code>ACTIVATION_REQUEST_TIMEOUT</code> therefore must not exceed
            <code>ACTIVATION_TIMEOUT</code>.
        </p>
    </td>
    <td>Required for MPA</td>
    <td><code>60</code></td>
    <td>1.2</td>
  </tr>
  <tr>
    <td><code>SMTP_HOST</code></td>
    <td><p>SMTP server to use for delivering notifications.</p></td>
    <td>Required for MPA</td>
    <td><code>smtp.gmail.com</code></td>
    <td>1.2</td>
  </tr>
  <tr>
    <td><code>SMTP_PORT</code></td>
    <td>
        <p>SMTP port to use for delivering notifications.</p>
        <p>Notice that <a href="https://cloud.google.com/compute/docs/tutorials/sending-mail#using_standard_email_ports">port 25
        is not allowed</a>.</p>
    </td>
    <td>Required for MPA</td>
    <td><code>587</code></td>
    <td>1.2</td>
  </tr>
  <tr>
    <td><code>SMTP_SENDER_NAME</code></td>
    <td><p>Name used as sender name in notifications.</p></td>
    <td>Required for MPA</td>
    <td><code>JIT Access</code></td>
    <td>1.2</td>
  </tr>
  <tr>
    <td><code>SMTP_ENABLE_STARTTLS</code></td>
    <td><p>Enable StartTLS (required by most mail servers).</p></td>
    <td>Required for MPA</td>
    <td><code>true</code></td>
    <td>1.2</td>
  </tr>
  <tr>
    <td><code>SMTP_SENDER_ADDRESS</code></td>
    <td><p>Email address to use for notifications.</p></td>
    <td>Required for MPA</td>
    <td></td>
    <td>1.2</td>
  </tr>
  <tr>
    <td><code>SMTP_USERNAME</code></td>
    <td><p>Username for SMTP authentication (optional, only required if your SMTP requires authentication).</p></td>
    <td>Optional</td>
    <td></td>
    <td>1.2</td>
  </tr>
  <tr>
    <td><code>SMTP_PASSWORD</code></td>
    <td>
        <p>Password for SMTP authentication (optional, only required if your SMTP requires authentication).</p>
        <p>If you're using Gmail to deliver emails, this must be an <a href="https://support.google.com/accounts/answer/185833?hl=en">app password</a>.</p>
    </td>
    <td>Optional</td>
    <td></td>
    <td>1.2</td>
  </tr>
  <tr>
    <td><code>SMTP_OPTIONS</code></td>
    <td>
        <p>
            Comma-separated list of additional <a href="https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html">JavaMail</a>
            options for delivering email. For example:
            <code>mail.smtp.connectiontimeout=60000,mail.smtp.writetimeout=30000</code>
        </p>
        <p>For most mail servers, no additional options are required.</p>
    </td>
    <td>Optional</td>
    <td></td>
    <td>1.2</td>
  </tr>
</table>

--- 

_Just-In-Time Access is an open-source project and not an officially supported Google product._

_All files in this repository are under the
[Apache License, Version 2.0](LICENSE.txt) unless noted otherwise._
