module github.com/chessfinder/chessfinder-faster-backend/src_go/download/process

go 1.21.1

require (
	github.com/aws/aws-lambda-go v1.41.0
	github.com/aws/aws-sdk-go v1.46.1
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/db v0.0.0-20231013195809-b1378607bcce
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging v0.0.0-00010101000000-000000000000
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/metrics v0.0.0-00010101000000-000000000000
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue v0.0.0-20231013195809-b1378607bcce
	github.com/google/uuid v1.3.1
	github.com/stretchr/testify v1.8.4
	github.com/wiremock/go-wiremock v1.8.0
	go.uber.org/zap v1.26.0
)

require (
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/batcher v0.0.0-20231013195809-b1378607bcce // indirect
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/jmespath/go-jmespath v0.4.0 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	go.uber.org/multierr v1.10.0 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
)


replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/api => ../../details/api

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue => ../../details/queue

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/batcher => ../../details/batcher

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/db => ../../details/db

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/metrics => ../../details/metrics

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging => ../../details/logging
