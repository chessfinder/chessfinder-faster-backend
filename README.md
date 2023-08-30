This application will allow to search games using partial information about the position that has happened during the game.

Soon it will be available

In order to run integration tests run
```docker compose -f ./src/it/resources/docker-compose_local.yaml --env-file ./src/it/resources/.env up```,
then
```aws --endpoint-url http://localhost:4566  s3api create-bucket --bucket chessfinder```,
then

[//]: # (```samlocal deploy --template-file template_resources.yaml --stack-name chessfinder --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND --s3-bucket chessfinder```,)
```samlocal deploy --template-file .infrastructure/db.yaml --stack-name chessfinder_dyanmodb --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND --s3-bucket chessfinder```,
```samlocal deploy --template-file .infrastructure/queue.yaml --stack-name chessfinder_sqs --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND --s3-bucket chessfinder```,
then in ```sbt```
```IntegrationTest / test```
