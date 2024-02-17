module github.com/chessfinder/chessfinder-faster-backend/src_go/details/notification

require (
	github.com/aws/aws-lambda-go v1.43.0
	github.com/aws/aws-sdk-go v1.49.13
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging v0.0.0-00010101000000-000000000000
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue v0.0.0-20231216145356-18e6f1386687
	github.com/google/uuid v1.2.0
	github.com/stretchr/testify v1.8.1
	github.com/wiremock/go-wiremock v1.8.0
	go.uber.org/zap v1.26.0
)

require (
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/jmespath/go-jmespath v0.4.0 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	go.uber.org/multierr v1.10.0 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
)

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging => ../../details/logging

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue => ../../details/queue

go 1.21.1
