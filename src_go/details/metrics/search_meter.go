package metrics

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/cloudwatch"
)

type SearchAttemtMeter struct {
	Namespace        string
	CloudWatchClient *cloudwatch.CloudWatch
}

type StatisticsType string

const (
	Total               StatisticsType = "Total"
	TotalSuccessful     StatisticsType = "TotalSuccessful"
	UniqueSuccessful    StatisticsType = "UniqueSuccessful"
	DuplicateSuccessful StatisticsType = "DuplicateSuccessful"
)

func (meter *SearchAttemtMeter) SearchStatistics(stat StatisticsType) (err error) {

	statDimension := &cloudwatch.Dimension{
		Name:  aws.String("StatisticsType"),
		Value: aws.String(string(stat)),
	}

	chessDotComDatum := &cloudwatch.MetricDatum{
		MetricName: aws.String("SearchMeter"),
		Unit:       aws.String("Count"),
		Value:      aws.Float64(1.0),
		Dimensions: []*cloudwatch.Dimension{
			statDimension,
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
