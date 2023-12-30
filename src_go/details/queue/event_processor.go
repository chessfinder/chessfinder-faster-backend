package queue

import (
	"github.com/aws/aws-lambda-go/events"
	"go.uber.org/zap"
)

type EventProcessor interface {
	ProcessSingle(event *events.SQSMessage, logger *zap.Logger) (eventFailure *events.SQSBatchItemFailure, err error)
}

func ProcessMultiple(
	sqsEvents events.SQSEvent,
	eventProcessor EventProcessor,
	logger *zap.Logger,
) (commandsProcessed events.SQSEventResponse) {

	logger.Info("Processing events in total", zap.Int("events", len(sqsEvents.Records)))

	failures := []events.SQSBatchItemFailure{}

	for _, events := range sqsEvents.Records {
		eventFailure, errOfTheMessage := eventProcessor.ProcessSingle(&events, logger)
		if errOfTheMessage != nil && eventFailure != nil {
			failures = append(failures, *eventFailure)
		}
	}

	if len(failures) > 0 {
		logger.Error("Some events failed", zap.Int("eventFailures", len(failures)))
		commandsProcessed.BatchItemFailures = failures
	}
	return
}
