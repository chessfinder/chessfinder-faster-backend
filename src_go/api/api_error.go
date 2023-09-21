package api

import (
	"encoding/json"

	"github.com/aws/aws-lambda-go/events"
)

type ApiError interface {
	ToResponseEvent() events.APIGatewayV2HTTPResponse
}

type BusinessError struct {
	Code string `json:"code"`
	Msg  string `json:"msg"`
}

func (businessError BusinessError) ToResponseEvent() (responseEvent events.APIGatewayV2HTTPResponse) {
	responseBody, err := json.Marshal(businessError)
	if err != nil {
		// FIXME log here
		panic(err)
	}

	responseEvent.Body = string(responseBody)
	responseEvent.StatusCode = 422
	return
}

func (businessError BusinessError) Error() string {
	return businessError.Msg
}

type ValidationError struct {
	Msg string
}

func (invalid ValidationError) ToResponseEvent() (responseEvent events.APIGatewayV2HTTPResponse) {
	responseEvent.Body = string(invalid.Msg)
	responseEvent.StatusCode = 400
	return
}

func (invalid ValidationError) Error() string {
	return invalid.Msg
}
