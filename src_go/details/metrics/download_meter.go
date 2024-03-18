package metrics

import (
	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/cloudwatch"
)

type DownloadMeter struct {
	Namespace        string
	CloudWatchClient *cloudwatch.CloudWatch
}

func (meter *DownloadMeter) SearchStatistics(amountOfGames int) (err error) {

	chessDotComDatum := &cloudwatch.MetricDatum{
		MetricName: aws.String("DownloadMeter"),
		Unit:       aws.String("Count"),
		Value:      aws.Float64(float64(amountOfGames)),
		Dimensions: []*cloudwatch.Dimension{},
	}

	_, err = meter.CloudWatchClient.PutMetricData(&cloudwatch.PutMetricDataInput{
		Namespace: aws.String(string(meter.Namespace)),
		MetricData: []*cloudwatch.MetricDatum{
			chessDotComDatum,
		},
	})
	return

}
