This article describes how you can configure the Just-in-Time Access to publish notifications to a 
[Pub/Sub topic :octicons-link-external-16:](https://cloud.google.com/pubsub/docs/create-topic) when one of the following events occur:

1. A user activates a role binding that permits self-approval
1. A user requests approval for activating a role binding that requires [multi-party approval](multi-party-approval.md)
1. A user approves an activation request

## Create a Pub/Sub topic

Create a Pub/Sub topic that JIT Access can publish messages to. The topic must reside in the same project as the JIT Access application.

1.  Set an environment variable to contain your project ID:

        gcloud config set project PROJECT_ID

    Replace `PROJECT_ID` with the ID of your project.

1.  Enable the Pub/Sub API:

        gcloud services enable pubsub.googleapis.com
    
1.  Initialize an environment variable for the Pub/Sub topic name:

        PUBSUB_TOPIC=TOPIC

    Replace <code>TOPIC</code> with a topic name, for example <code>jitaccess-events</code>.

1.  Create the Pub/Sub topic:

        gcloud pubsub topics create $PUBSUB_TOPIC
    
1.  Grant the **Pub/Sub Publisher** role (`roles/pubsub.publisher`) to the application's service account:

        SERVICE_ACCOUNT=$(gcloud run services describe jitaccess --format "value(spec.template.spec.serviceAccountName)")
        gcloud pubsub topics add-iam-policy-binding $PUBSUB_TOPIC \
          --member="serviceAccount:$SERVICE_ACCOUNT" \
          --role="roles/pubsub.publisher"

## Configure JIT Access

You now update the configuration and redeploy the Just-in-Time Access application:

1.  Clone the GitHub repository and switch to the `latest` branch:

        git clone https://github.com/GoogleCloudPlatform/jit-access.git
        cd jit-access/sources
        git checkout latest

1.  Download the configuration file that you used previously to deploy the application and save it to a file app.yaml:

    === "App Engine"

            APPENGINE_VERSION=$(gcloud app versions list --service default --hide-no-traffic --format "value(version.id)")
            APPENGINE_APPYAML_URL=$(gcloud app versions describe $APPENGINE_VERSION --service default --format "value(deployment.files.'app.yaml'.sourceUrl)")
        
            curl -H "Authorization: Bearer $(gcloud auth print-access-token)" $APPENGINE_APPYAML_URL -o app.yaml

    === "Cloud Run"

            gcloud run services describe jitaccess --format yaml > app.yaml

1.  Open the file `app.yaml` in an editor and add the following [configuration option](configuration-options.md):

        NOTIFICATION_TOPIC: topic

    Replace `topic` with the name of the Pub/Sub topic you created in a previous step.

1. Deploy the application with the updated configuration:

    === "App Engine"

            sed -i 's/java11/java17/g' app.yaml
            gcloud app deploy --appyaml app.yaml

    === "Cloud Run"

            PROJECT_ID=$(gcloud config get-value core/project)

            docker build -t gcr.io/$PROJECT_ID/jitaccess:latest .
            docker push gcr.io/$PROJECT_ID/jitaccess:latest

            IMAGE=$(docker inspect --format='{{index .RepoDigests 0}}'  gcr.io/$PROJECT_ID/jitaccess)
            sed -i "s|image:.*|image: $IMAGE|g" app.yaml

            gcloud run services replace app.yaml
