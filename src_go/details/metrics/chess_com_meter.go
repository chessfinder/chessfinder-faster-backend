package metrics

import (
	"strconv"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/cloudwatch"
)

type ChessDotComMeter struct {
	Namespace        Namespace
	CloudWatchClient *cloudwatch.CloudWatch
}

type ChessDotComAction string

const (
	GetProfile  ChessDotComAction = "GetProfile"
	GetArchives ChessDotComAction = "GetClubs"
	GetGames    ChessDotComAction = "GetGames"
)

func (meter *ChessDotComMeter) ChessDotComStatistics(action ChessDotComAction, statusCode int) (err error) {

	actionDimension := &cloudwatch.Dimension{
		Name:  aws.String("Action"),
		Value: aws.String(string(action)),
	}

	statusCodeDimension := &cloudwatch.Dimension{
		Name:  aws.String("StatusCode"),
		Value: aws.String(strconv.Itoa(statusCode)),
	}

	chessDotComDatum := &cloudwatch.MetricDatum{
		MetricName: aws.String("ChessDotComMeter"),
		Unit:       aws.String("Count"),
		Value:      aws.Float64(1.0),
		Dimensions: []*cloudwatch.Dimension{
			actionDimension,
			statusCodeDimension,
		},
	}

	_, err = meter.CloudWatchClient.PutMetricData(&cloudwatch.PutMetricDataInput{
		Namespace: aws.String(string(meter.Namespace)),
		MetricData: []*cloudwatch.MetricDatum{
			chessDotComDatum,
		},
	})
	return

}
