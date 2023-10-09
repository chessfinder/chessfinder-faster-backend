package db

import (
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/dynamodb"
	"github.com/aws/aws-sdk-go/service/dynamodb/dynamodbattribute"
)

type ZuluDateTime struct {
	dynamodbattribute.UnixTime
}

func ZuluDateTimeFromString(s string) (ZuluDateTime, error) {
	t, err := time.Parse("2006-01-02T15:04:05.000Z", s)
	if err != nil {
		return ZuluDateTime{}, err
	}
	return ZuluDateTime{dynamodbattribute.UnixTime(t)}, nil
}

func (zulu ZuluDateTime) String() string {
	return time.Time(zulu.UnixTime).UTC().Format("2006-01-02T15:04:05.000Z")
}

func (zulu ZuluDateTime) ToTime() time.Time {
	return time.Time(zulu.UnixTime)
}

func (zulu ZuluDateTime) ToDate() ZuluDate {
	return Zuludate(zulu.ToTime())
}

func Zuludatetime(t time.Time) ZuluDateTime {
	return ZuluDateTime{dynamodbattribute.UnixTime(t)}
}

func (e ZuluDateTime) MarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	av.S = aws.String(e.String())
	av.N = nil
	return nil
}

func (e *ZuluDateTime) UnmarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	if av.S == nil || *av.S == "" {
		return nil
	}
	t, err := time.Parse("2006-01-02T15:04:05.000Z", *av.S)
	if err != nil {
		return err
	}
	e.UnixTime = dynamodbattribute.UnixTime(t)
	return nil
}

type ZuluDate struct {
	dynamodbattribute.UnixTime
}

func ZuluDateFromString(s string) (ZuluDate, error) {
	t, err := time.Parse("2006-01-02", s)
	if err != nil {
		return ZuluDate{}, err
	}
	return ZuluDate{dynamodbattribute.UnixTime(t)}, nil
}

func (zulu ZuluDate) String() string {
	return time.Time(zulu.UnixTime).UTC().Format("2006-01-02")
}

// test this against time zones
func (zulu ZuluDate) ToTime() time.Time {
	dateTime := time.Time(zulu.UnixTime)
	year, month, day := dateTime.Date()
	date := time.Date(year, month, day, 0, 0, 0, 0, time.UTC)
	return date
}

func Zuludate(t time.Time) ZuluDate {
	return ZuluDate{dynamodbattribute.UnixTime(t)}
}

func (e ZuluDate) MarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	av.S = aws.String(e.String())
	av.N = nil
	return nil
}

func (e *ZuluDate) UnmarshalDynamoDBAttributeValue(av *dynamodb.AttributeValue) error {
	if av.S == nil || *av.S == "" {
		return nil
	}
	t, err := time.Parse("2006-01-02", *av.S)
	if err != nil {
		return err
	}
	e.UnixTime = dynamodbattribute.UnixTime(t)
	return nil
}
