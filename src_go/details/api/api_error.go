package api

import (
	"encoding/json"

	"github.com/aws/aws-lambda-go/events"
)

type ApiError interface {
	toResponseEvent() events.APIGatewayV2HTTPResponse
}

type BusinessError struct {
	Code    string `json:"code"`
	Message string `json:"message"`
}

var ServiceOverloaded = BusinessError{
	Message: "Service is overloaded. Please try again later.",
	Code:    "SERVICE_OVERLOADED",
}

func (businessError BusinessError) toResponseEvent() (responseEvent events.APIGatewayV2HTTPResponse) {
	responseBody, err := json.Marshal(businessError)
	if err != nil {
		// FIXME log here
		panic(err)
	}

	responseEvent.Body = string(responseBody)
	responseEvent.StatusCode = 422
	responseEvent.Headers = map[string]string{
		"Content-Type": "application/json",
	}
	return
}

func (businessError BusinessError) Error() string {
	return businessError.Message
}

type ValidationError struct {
	Message string `json:"message"`
}

var InvalidBody = ValidationError{
	Message: "Invalid body",
}

func (invalid ValidationError) toResponseEvent() (responseEvent events.APIGatewayV2HTTPResponse) {
	responseEvent.Body = string(invalid.Message)
	responseEvent.StatusCode = 400
	responseEvent.Headers = map[string]string{
		"Content-Type": "application/json",
	}
	return
}

func (invalid ValidationError) Error() string {
	return invalid.Message
}

func WithRecover(handler func(*events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error)) func(*events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error) {
	recovered := func(requestEvent *events.APIGatewayV2HTTPRequest) (events.APIGatewayV2HTTPResponse, error) {
		responseEvent, err := handler(requestEvent)
		if err != nil {
			switch err := err.(type) {
			case ApiError:
				responseEvent = err.toResponseEvent()
				return responseEvent, nil
			default:
				panic(err)
			}
		}
		return responseEvent, nil
	}
	return recovered
}
