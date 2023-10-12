module github.com/chessfinder/chessfinder-faster-backend/src_go/search/process

go 1.21.0

require github.com/chessfinder/chessfinder-faster-backend/src_go/details/db v0.0.0-00010101000000-000000000000

require (
	github.com/aws/aws-lambda-go v1.41.0
	github.com/aws/aws-sdk-go v1.45.24
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue v0.0.0-00010101000000-000000000000
	go.uber.org/zap v1.26.0
)

require (
	github.com/jmespath/go-jmespath v0.4.0 // indirect
	go.uber.org/multierr v1.10.0 // indirect
)

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/db => ../../details/db

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue => ../../details/queue
