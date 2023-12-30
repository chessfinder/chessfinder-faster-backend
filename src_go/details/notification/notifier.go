package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"

	"github.com/aws/aws-lambda-go/events"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"go.uber.org/zap"
)

type Notifier struct {
	telegramUrl             string
	telegramBotApiKey       string
	telegramChatId          int64
	telegramInsightsAlarms  []string
	telegramInsightsTopicId int64
	telegramHealthTopicId   int64
	telegramHealthAlarms    []string
}

func (notifier *Notifier) Notify(commands events.SQSEvent) events.SQSEventResponse {
	logger := logging.MustCreateZuluTimeLogger()
	defer logger.Sync()
	failedEvents := queue.ProcessMultiple(commands, notifier, logger)
	return failedEvents
}

func (notifier *Notifier) ProcessSingle(
	message *events.SQSMessage,
	logger *zap.Logger,
) (commandProcessed *events.SQSBatchItemFailure, err error) {

	telegramClient := &http.Client{}

	snsNotification := events.CloudWatchAlarmSNSPayload{}
	err = json.Unmarshal([]byte(message.Body), &snsNotification)
	if err != nil {
		logger.Error("impossible to unmarshal the SNS notification!")
		return
	}

	logger = logger.With(zap.String("alarm_name", snsNotification.AlarmName))
	logger = logger.With(zap.String("state", snsNotification.NewStateValue))
	logger.Info("received a SNS notification")

	var topicId *int64
	var messagePrefix string

	if snsNotification.NewStateValue == "INSUFFICIENT_DATA" {
		logger.Info("ignoring the notification because the state is INSUFFICIENT_DATA")
		return
	}

	for _, alarmName := range notifier.telegramInsightsAlarms {
		if alarmName == snsNotification.AlarmName {
			topicId = &notifier.telegramInsightsTopicId
			if snsNotification.NewStateValue == "OK" {
				messagePrefix = "🏆🍾💰"
			} else {
				messagePrefix = "🤬💩🍆"
			}
		}
	}

	for _, alarmName := range notifier.telegramHealthAlarms {
		if alarmName == snsNotification.AlarmName {
			topicId = &notifier.telegramHealthTopicId
			if snsNotification.NewStateValue == "OK" {
				messagePrefix = "💚☕️🏖"
			} else {
				messagePrefix = "🧨☠️🚨"
			}
		}
	}

	if topicId == nil {
		messagePrefix = "🚨☎️📢"
	}

	messageText := messagePrefix + " " + snsNotification.AlarmName + "\n" + snsNotification.AlarmDescription

	notifiction := Notification{
		ChatId:          notifier.telegramChatId,
		Text:            messageText,
		MessageTheardId: topicId,
	}

	notificationJson, err := json.Marshal(notifiction)
	if err != nil {
		logger.Error("impossible to marshal the notification!")
		return
	}

	url := notifier.telegramUrl + "/bot" + notifier.telegramBotApiKey + "/sendMessage"

	notificationRequest, err := http.NewRequest("POST", url, bytes.NewBuffer(notificationJson))
	if err != nil {
		logger.Error("impossible to create the notification request!")
		return
	}

	notificationRequest.Header.Set("Accept", "application/json")
	notificationRequest.Header.Set("Content-Type", "application/json")

	notificationResponse, err := telegramClient.Do(notificationRequest)

	if err != nil {
		logger.Error("impossible to send the notification!")
		return
	}

	defer notificationResponse.Body.Close()

	if notificationResponse.StatusCode != 200 {
		logger.Error("unexpected status code from telegram", zap.Int("statusCode", notificationResponse.StatusCode))
		return
	}

	responseBodyBytes, err := io.ReadAll(notificationResponse.Body)
	if err != nil {
		logger.Error("impossible to read the response body from chess.com!", zap.Error(err))
		return
	}

	notificationResult := NotificationResult{}

	err = json.Unmarshal(responseBodyBytes, &notificationResult)
	if err != nil {
		logger.Error("impossible to decode the notification response!")
		return
	}

	if !notificationResult.Ok {
		logger.Error("notification not sent!")
	}

	logger.Info("notification sent!")

	return

}
