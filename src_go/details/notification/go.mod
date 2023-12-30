module github.com/chessfinder/chessfinder-faster-backend/src_go/details/notification

require (
	github.com/aws/aws-lambda-go v1.43.0
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging v0.0.0-00010101000000-000000000000
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue v0.0.0-20231216145356-18e6f1386687
	go.uber.org/zap v1.26.0
)

require (
	github.com/aws/aws-sdk-go v1.49.13 // indirect
	github.com/jmespath/go-jmespath v0.4.0 // indirect
	go.uber.org/multierr v1.10.0 // indirect
)

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging => ../../details/logging

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue => ../../details/queue

go 1.21.0
