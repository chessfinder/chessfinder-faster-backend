package db

import (
	"testing"
	"time"

	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
	"github.com/stretchr/testify/assert"
)

func TestZuluDate_can_be_parsed_correctly(t *testing.T) {

	dateInString := "2020-06-01"
	actualZuluDate, err := ZuluDateFromString(dateInString)
	expectedZuluDate := ZuluDate{dynamodbattribute.UnixTime(time.Date(2020, time.June, 1, 0, 0, 0, 0, time.UTC))}

	assert.Nil(t, err)
	assert.Equal(t, actualZuluDate, expectedZuluDate)
}

func TestZuluDate_can_be_converted_into_string_correctly(t *testing.T) {

	zuluDate := ZuluDate{dynamodbattribute.UnixTime(time.Date(2020, time.June, 1, 0, 0, 0, 0, time.UTC))}
	actualString := zuluDate.String()
	expectedString := "2020-06-01"

	assert.Equal(t, actualString, expectedString)
}

func TestZuluDateTime_can_be_parsed_correctly(t *testing.T) {

	dateInString := "2020-06-01T12:34:56.000Z"
	actualZuluDateTime, err := ZuluDateTimeFromString(dateInString)
	expectedZuluDateTime := ZuluDateTime{dynamodbattribute.UnixTime(time.Date(2020, time.June, 1, 12, 34, 56, 0, time.UTC))}

	assert.Nil(t, err)
	assert.Equal(t, actualZuluDateTime, expectedZuluDateTime)
}

func TestZuluDateTime_can_be_converted_into_string_correctly(t *testing.T) {

	zuluDateTime := ZuluDateTime{dynamodbattribute.UnixTime(time.Date(2020, time.June, 1, 12, 34, 56, 0, time.UTC))}
	actualString := zuluDateTime.String()
	expectedString := "2020-06-01T12:34:56.000Z"

	assert.Equal(t, actualString, expectedString)
}
