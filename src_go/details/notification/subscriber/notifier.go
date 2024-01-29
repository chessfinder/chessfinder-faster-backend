package main

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"

	"github.com/aws/aws-lambda-go/events"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/logging"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/notification"
	"github.com/chessfinder/chessfinder-faster-backend/src_go/details/queue"
	"go.uber.org/zap"
)

type Notifier struct {
	telegramUrl           string
	telegramBotApiKey     string
	telegramChatId        int64
	telegramReportTopicId int64
	telegramAlarmTopicId  int64
}

func (notifier *Notifier) Notify(commands events.SQSEvent) (events.SQSEventResponse, error) {
	logger := logging.MustCreateZuluTimeLogger()
	defer logger.Sync()
	failedEvents := queue.ProcessMultiple(commands, notifier, logger)
	return failedEvents, nil
}

func (notifier *Notifier) ProcessSingle(
	message *events.SQSMessage,
	logger *zap.Logger,
) (commandProcessed *events.SQSBatchItemFailure, err error) {
	notificationType, ok := message.MessageAttributes["NotificationType"]
	if !ok {
		notifier.handleAlarm(message, logger)
		return
	}

	if notificationType.StringValue == nil {
		logger.Error("NotificationType is nil!")
		return
	}

	if *notificationType.StringValue == "Job" {
		notifier.handleNotification(message, logger)
		return
	}
	return
}

func (notifier *Notifier) handleNotification(message *events.SQSMessage, logger *zap.Logger) (commandProcessed *events.SQSBatchItemFailure, err error) {
	telegramClient := &http.Client{}
	businessNotification := notification.BusinessNotification{}
	err = json.Unmarshal([]byte(message.Body), &businessNotification)
	if err != nil {
		logger.Error("impossible to unmarshal the SNS notification!")
		return
	}

	logger = logger.With(zap.String("notifier_request_id", businessNotification.RequestId))
	logger.Info("received a Business notification")

	messageText := "🤖 " + businessNotification.RequestId + " 🤖" + "\n" + businessNotification.Message

	telegramMessage := notification.TelegramMessage{
		ChatId:          notifier.telegramChatId,
		Text:            messageText,
		MessageTheardId: &notifier.telegramReportTopicId,
	}

	notificationJson, err := json.Marshal(telegramMessage)
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

	telegramResponse := notification.TelegramMessageResponse{}

	err = json.Unmarshal(responseBodyBytes, &telegramResponse)
	if err != nil {
		logger.Error("impossible to decode the notification response!")
		return
	}

	if !telegramResponse.Ok {
		logger.Error("notification not sent!")
	}

	logger.Info("notification sent!")

	return
}

func (notifier *Notifier) handleAlarm(message *events.SQSMessage, logger *zap.Logger) (commandProcessed *events.SQSBatchItemFailure, err error) {
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

	if snsNotification.NewStateValue == "INSUFFICIENT_DATA" {
		logger.Info("ignoring the notification because the state is INSUFFICIENT_DATA")
		return
	}

	var messagePrefix string
	var messageSuffix string

	if snsNotification.NewStateValue == "OK" {
		messagePrefix = "🏆🍾💰"
		messageSuffix = "💚☕️🏖"
	} else {
		messagePrefix = "🧨☠️🚨"
		messageSuffix = "🤬💩🍆"
	}

	messageText := messagePrefix + " " + snsNotification.AlarmName + " " + messageSuffix + "\n" + snsNotification.AlarmDescription

	telegramMessage := notification.TelegramMessage{
		ChatId:          notifier.telegramChatId,
		Text:            messageText,
		MessageTheardId: &notifier.telegramAlarmTopicId,
	}

	notificationJson, err := json.Marshal(telegramMessage)
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

	telegramResponse := notification.TelegramMessageResponse{}

	err = json.Unmarshal(responseBodyBytes, &telegramResponse)
	if err != nil {
		logger.Error("impossible to decode the notification response!")
		return
	}

	if !telegramResponse.Ok {
		logger.Error("notification not sent!")
	}

	logger.Info("notification sent!")

	return
}
