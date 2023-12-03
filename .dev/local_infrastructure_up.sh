#! /bin/sh

aws --endpoint-url http://localhost:4566  s3api create-bucket --bucket chessfinder
samlocal deploy --template-file .infrastructure/db.yaml --stack-name chessfinder_dynamodb --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND --s3-bucket chessfinder --parameter-overrides TheStackName=chessfinder_dynamodb
samlocal deploy --template-file .infrastructure/queue.yaml --stack-name chessfinder_sqs --capabilities CAPABILITY_NAMED_IAM CAPABILITY_AUTO_EXPAND --s3-bucket chessfinder --parameter-overrides TheStackName=chessfinder_sqs
