# Configuration

You can customize the behavior of the Just-In-Time Access application by editing the `env_variables` section of the
[`app.yaml` configuration file](https://cloud.google.com/appengine/docs/standard/java-gen2/config/appref) and
redeploying the application.

The Just-In-Time Access application supports the following environment variables:



<table>
  <tr>
   <th>Name</th>
   <th>Description</th>
  </tr>
  <tr>
   <td>
    <code>RESOURCE_SCOPE</code>
   </td>
   <td>The part of the Google Cloud resource hierarchy that's managed by using this application. You can use one of the following values:
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
  </tr>
  <tr>
   <td>
    <code>ELEVATION_DURATION</code>
   </td>
   <td>
    The duration in minutes for which privileged access is granted. The default is <code>2</code>.
   </td>
  </tr>
  <tr>
   <td>
    <code>JUSTIFICATION_HINT</code>
   </td>
   <td>
    A hint that's displayed to the user that indicates the justification to provide, such as a case number. This value is free form and can include any information that makes sense for your scenario.
   </td>
  </tr>
  <tr>
   <td>
    <code>JUSTIFICATION_PATTERN</code>
   </td>
   <td>
    A regular expression that a justification has to match. For example, you can use this setting to validate that justifications include a ticket number or that they follow a certain convention, such as a pattern for an issue identification or support case ID.
   </td>
  </tr>
</table>

--- 

_Just-In-Time Access is an open-source project and not an officially supported Google product._

_All files in this repository are under the
[Apache License, Version 2.0](LICENSE.txt) unless noted otherwise._
