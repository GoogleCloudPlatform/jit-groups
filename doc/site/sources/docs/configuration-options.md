You can customize the behavior of the Just-In-Time Access application by setting environment variables
in your [AppEngine configuration file :octicons-link-external-16:](https://cloud.google.com/appengine/docs/standard/java-gen2/config/appref)
or [Cloud Run service YAML :octicons-link-external-16:](https://cloud.google.com/run/docs/reference/yaml/v1).

The following table lists all available configuration options.

## Basic configuration

<table>
  <tr>
    <th>Name</th>
    <th>Description</th>
    <th>Required</th>
    <th>Default</th>
    <th>Available since</th>
  </tr>
  <tr>
    <td>
        <code>RESOURCE_SCOPE</code>
    </td>
    <td>
        <p>
            The organization, folder, or project that JIT Access can access and manage. The resource scope constrains:
            <ul>
                <li>
                    The set of projects that you can grant just-in-time access to: For example, if you specify a 
                    folder or organization as scope, then you can only grant users just-in-time access to projects within this
                    folder or organization. 
                </li>
                <li>
                    The IAM policies that JIT Access analyzes to determine eligible access: For example, if you specify a 
                    folder as scope, JIT Access analyzes the IAM policies of this folder and all its sub-folders and projects
                    to determine eligible access, but ignores IAM policies inherited from the organization node.
                </li>
                <li>
                    The types of <a href='https://cloud.google.com/iam/docs/creating-custom-roles'>custom roles</a> that you 
                    can use to grant just-in-time access (as an alternative to predefined roles):
                    If you set the resource scope to a folder or project, then
                    you can use custom roles that have been defined in the respective project. If you set the scope to the entire organization,
                    you can use all custom roles, including custom roles that have been defined at the organization level.
                </li>
            </ul>
        </p>
        <p>You can use one of the following values:</p>
        <p>
            <ul>
                <li><code>organizations/ORGANIZATION_ID</code> (all projects)</li>
                <li><code>folders/FOLDER_ID</code> (projects underneath a specific folder, including nested folders)</li>
                <li><code>projects/PROJECT_ID</code> (specific project)</li>
            </ul>
        </p>
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
        <code>RESOURCE_CATALOG</code>
    </td>
    <td>
        <p>Approach and API to use for finding eligible role bindings.</p>
        <p>For more information about catalogs, see 
        <a href='https://github.com/GoogleCloudPlatform/jit-access/wiki/Switch-to-a-different-catalog'>Switch to a different catalog</a>.</p>
    </td>
    <td>Required</td>
    <td><code>PolicyAnalyzer</code></td>
    <td>1.6</td>
  </tr>
  <tr>
    <td>
        <code>RESOURCE_CUSTOMER_ID</code>
    </td>
    <td>
       <p>Customer ID of your Cloud Identity or Workspace account</p>
       <p>For more information about how to find this ID, see <a href='https://support.google.com/a/answer/10070793'>Find your customer ID</a>.</p>
    </td>
    <td>Required for the <tt>AssetInventory</tt> catalog</td>
    <td></td>
    <td>1.6</td>
  </tr>
  <tr>
    <td>
        <code>ACTIVATION_TIMEOUT</code>,
        <br><br>Deprecated:<br>
        <code>ELEVATION_DURATION</code>
    </td>
    <td>
        <p>Maximum duration (in minutes) for which users can request to activate a role.
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
    <td>
        <code>ACTIVATION_REQUEST_MAX_ROLES</code>
    </td>
    <td>
        <p>Maximum number of roles that users can activate in a single request.</p>
    </td>
    <td>Required</td>
    <td><code>10</code></td>
    <td>1.4.1</td>
  </tr>
  <tr>
    <td>
        <code>AVAILABLE_PROJECTS_QUERY</code>
    </td>
    <td>
        <p>Query to use for project auto-completer.</p>
        <p>When not configured, the application uses the Policy Analyzer API to determine the list of projects shown in the project auto-completer. The auto-completer only lists projects that the user has eligible access to.<p>
        <p>When you configure this variable, the application instead performs a <a href='https://cloud.google.com/resource-manager/reference/rest/v3/projects/search'>search</a> to determine the list of projects. This method is faster, but can lead to unintended information disclosure where users are suggested projects they don't have access to.<p>
        <p>Set this variable to any query supported by <a href='https://cloud.google.com/resource-manager/reference/rest/v3/projects/search'><code>projects.search</code></a>, for example <code>state:ACTIVE</code> and grant the service account the <i>Browser</i> role (or an equivalent role that includes the <code>resourcemanager.projects.get</code> permission) on relevant projects.</p> 
    </td>
    <td>Optional</td>
    <td></td>
    <td>1.5</td>
  </tr>
</table>

## Multi-party approval

<table>
  <tr>
    <th>Name</th>
    <th>Description</th>
    <th>Required</th>
    <th>Default</th>
    <th>Available since</th>
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
    <td>
        <code>ACTIVATION_REQUEST_MIN_REVIEWERS</code>
    </td>
    <td>
        <p>Minimum number of reviewers for approval requests.</p>
        <p>
        If you set this to a value larger than <code>1</code>, users need to select
        multiple peers when requesting approval, but obtaining approval from
        a single reviewer is still sufficient to activate access.
      </p>
    </td>
    <td>Required for MPA</td>
    <td><code>1</code></td>
    <td>1.4</td>
  </tr>
  <tr>
    <td>
        <code>ACTIVATION_REQUEST_MAX_REVIEWERS</code>
    </td>
    <td>
        <p>Maximum number of reviewers for approval requests.</p>
    </td>
    <td>Required for MPA</td>
    <td><code>10</code></td>
    <td>1.4</td>
  </tr>
  <tr>
    <td><code>SMTP_HOST</code></td>
    <td><p>SMTP server to use for delivering notifications.</p></td>
    <td>Required for MPA</td>
    <td><code>smtp.gmail&#8203.com</code></td>
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
    <td><code>SMTP_SECRET</code></td>
    <td>
        <p>Path to a Secrets Manager secret that contains the password for SMTP authentication. You can use this option as an alternative to <code>SMTP_PASSWORD</code>.</p>
        <p>The path must be in the format <code>projects/PROJECTID/secrets/ SECRETID/versions/latest</code>.</p>
        <p>If you're using Gmail to deliver emails, this must be an <a href="https://support.google.com/accounts/answer/185833?hl=en">app password</a>.</p>
    </td>
    <td>Optional</td>
    <td></td>
    <td>1.4</td>
  </tr>
  <tr>
    <td><code>SMTP_OPTIONS</code></td>
    <td>
        <p>
            Comma-separated list of additional <a href="https://javaee.github.io/javamail/docs/api/com/sun/mail/smtp/package-summary.html">JavaMail</a>
            options for delivering email. For example:
            <code>mail.smtp.connectiontimeout=60000, mail.smtp.writetimeout=30000</code>
        </p>
        <p>For most mail servers, no additional options are required.</p>
    </td>
    <td>Optional</td>
    <td></td>
    <td>1.2</td>
  </tr>
  <tr>
    <td><code>SMTP_ADDRESS_MAPPING</code></td>
    <td>
        <p>
            <a href="https://github.com/google/cel-spec/blob/master/doc/intro.md">CEL expression</a> for deriving
            a user's email address from their Cloud Identity/Workspace user ID.
        </p>
        <p>
            By default, JIT Accesses uses the Cloud Identity/Workspace user ID (such as alice@example.com) as
            email address to deliver notifications to. If some or all of your Cloud Identity/Workspace user IDs 
            do not correspond to valid email addresses, use this setting to specify a CEL expression that derives a valid email address.
        </p>
        <p>
            CEL expressions can use <a href="https://github.com/google/cel-spec/blob/master/doc/langdef.md#list-of-standard-definitions">standard functions</a>
            and the <a href="https://cloud.google.com/iam/docs/conditions-attribute-reference#extract"><code>extract()</code></a> function.
        </p>
        <p>
            For example, the following expression replaces the domain <code>example.com</code> with <code>test.example.com</code> for all users:
        </p>
        <p>
            <code>user.email.extract('{handle}@example.com') + '@test.example.com'</code>
        </p>
        <p>
            If you're using multiple domains and only need to substitute one of them, you can use conditional
            statements. For example:
        </p>
        <p>
            <code>user.email.endsWith('@external.example.com') 
                ? user.email.extract('{handle}@external.example.com') + '@otherdomain.example' 
                : user.email
            </code>
        </p>
    </td>
    <td>Optional</td>
    <td></td>
    <td>1.7</td>
  </tr>
</table>

## Notifications

<table>
  <tr>
    <th>Name</th>
    <th>Description</th>
    <th>Required</th>
    <th>Default</th>
    <th>Available since</th>
  </tr>
  <tr>
    <td>
        <code>NOTIFICATION_TIMEZONE</code>
    </td>
    <td>
        <p>Timezone to use for dates in notification emails.</p>
        <p>
            The value must be a valid identifier from the IANA Time Zone Database (TZDB),
            for example <code>Australia/Melbourne</code> or <code>Europe/Berlin</code>.
        </p>
    </td>
    <td>Required for MPA</td>
    <td>UTC</td>
    <td>1.2</td>
  </tr>
  <tr>
    <td>
        <code>NOTIFICATION_TOPIC</code>
    </td>
    <td>
        <p>Name of a <a href='https://cloud.google.com/pubsub/docs/create-topic'>Pub/Sub topic</a> to post notifications to, for example <code>jitaccess-events</code>.</p>
        <p>
            When you configure this variable, JIT Access posts a notification message to the Pub/Sub topic whenever
            a user self-activates a role, requests MPA-approval for a role, or is granted MPA-approval. Other applications
            can consume these messages to implement additional logic, such as posting to chat rooms or triggering additional workflows.
        </p>
        <p>
            When you don't configure this variable, JIT Access doesn't post any Pub/Sub messages.
        </p>
        <p>The topic must be in the same project as the application.</p>
    </td>
    <td>Optional</td>
    <td></td>
    <td>1.5</td>
  </tr>
</table>

## Networking

<table>
  <tr>
    <th>Name</th>
    <th>Description</th>
    <th>Required</th>
    <th>Default</th>
    <th>Available since</th>
  </tr>
  <tr>
    <td>
        <code>BACKEND_CONNECT_TIMEOUT</code>
    </td>
    <td>
        <p>Connection timeout for Google API requests, in seconds.</p>
    </td>
    <td>Optional</td>
    <td><code>5</code></td>
    <td>1.5</td>
  </tr>
  <tr>
    <td>
        <code>BACKEND_READ_TIMEOUT</code>
    </td>
    <td>
        <p>Read timeout for Google API requests, in seconds.</p>
    </td>
    <td>Optional</td>
    <td><code>20</code></td>
    <td>1.5</td>
  </tr>
  <tr>
    <td>
        <code>BACKEND_WRITE_TIMEOUT</code>
    </td>
    <td>
        <p>Write timeout for Google API requests, in seconds.</p>
    </td>
    <td>Optional</td>
    <td><code>5</code></td>
    <td>1.5</td>
  </tr>
</table>
