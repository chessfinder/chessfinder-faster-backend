module github.com/chessfinder/chessfinder-faster-backend/src_go/download/check_status

go 1.21.0

require (
	github.com/aws/aws-lambda-go v1.41.0
	github.com/aws/aws-sdk-go v1.45.24
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/api v0.0.0-00010101000000-000000000000
	github.com/chessfinder/chessfinder-faster-backend/src_go/details/db v0.0.0-00010101000000-000000000000

	github.com/google/uuid v1.2.0
	go.uber.org/zap v1.25.0
// github.com/chessfinder/chessfinder-faster-backend/api
)

require (
	github.com/davecgh/go-spew v1.1.1 // indirect
	github.com/pmezard/go-difflib v1.0.0 // indirect
	gopkg.in/yaml.v3 v3.0.1 // indirect
)

require (
	github.com/jmespath/go-jmespath v0.4.0 // indirect
	github.com/stretchr/testify v1.8.4
	go.uber.org/multierr v1.10.0 // indirect
)

// require github.com/chessfinder/chessfinder-faster-backend/src_go/api v0.0.0

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/api => ../../details/api

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue => ../../details/queue

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/batcher => ../../details/batcher

replace github.com/chessfinder/chessfinder-faster-backend/src_go/details/db => ../../details/db
