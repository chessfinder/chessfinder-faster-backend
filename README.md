This application will allow you to search your games using partial information about your position that has happened during the game.

Soon it will be available

In order to run integration tests run 
```aws --endpoint-url http://localhost:4566  s3api create-bucket --bucket chessfinder```
then
```samlocal deploy --template-file template_resources.yaml --stack-name chessfinder --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND --s3-bucket chessfinder```
then
```docker compose -f ./src/it/resources docker-compose_local.yaml --env-file ./src/it/resources/.env up``` beforehand.
