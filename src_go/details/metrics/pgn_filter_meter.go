package metrics

import (
	"time"

	"github.com/aws/aws-sdk-go/aws"
	"github.com/aws/aws-sdk-go/service/cloudwatch"
)

type PgnFilterMeter struct {
	Namespace        string
	CloudWatchClient *cloudwatch.CloudWatch
}

type PgnFilterType string

const (
	NotNil PgnFilterType = "NotNil"
	MyOwn  PgnFilterType = "MyOwn"
)

func (meter *PgnFilterMeter) PgnFilterStatistics(filterType PgnFilterType, durationInNanos time.Duration) (err error) {

	typeDimension := &cloudwatch.Dimension{
		Name:  aws.String("Type"),
		Value: aws.String(string(filterType)),
	}

	durationInMicros := durationInNanos.Microseconds()
	chessDotComDatum := &cloudwatch.MetricDatum{
		MetricName: aws.String("PgnFilterMeter"),
		Unit:       aws.String("Microseconds"),
		Value:      aws.Float64(float64(durationInMicros)),
		Dimensions: []*cloudwatch.Dimension{
			typeDimension,
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
