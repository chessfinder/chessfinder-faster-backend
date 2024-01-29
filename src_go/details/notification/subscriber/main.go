package main

import (
	"errors"
	"os"
	"strconv"

	"github.com/aws/aws-lambda-go/lambda"
)

func main() {

	telegramUrl, telegramUrlExists := os.LookupEnv("TELEGRAM_URL")
	if !telegramUrlExists {
		panic(errors.New("TELEGRAM_URL is missing"))
	}

	telegramBotApiKey, telegramBotApiKeyExists := os.LookupEnv("TELEGRAM_BOT_API_KEY")
	if !telegramBotApiKeyExists {
		panic(errors.New("TELEGRAM_BOT_API_KEY is missing"))
	}

	telegramChatIdCandidate, telegramChatIdExists := os.LookupEnv("TELEGRAM_CHAT_ID")
	if !telegramChatIdExists {
		panic(errors.New("TELEGRAM_CHAT_ID is missing"))
	}

	telegramChatId, err := strconv.ParseInt(telegramChatIdCandidate, 10, 64)
	if err != nil {
		panic(err)
	}

	telegramReportTopicIdCandidate, telegramReportTopicIdExists := os.LookupEnv("TELEGRAM_REPORT_TOPIC_ID")
	if !telegramReportTopicIdExists {
		panic(errors.New("TELEGRAM_REPORT_TOPIC_ID is missing"))
	}

	telegramReportTopicId, err := strconv.ParseInt(telegramReportTopicIdCandidate, 10, 64)
	if err != nil {
		panic(err)
	}

	telegramAlarmTopicIdCandidate, telegramAlarmTopicIdExists := os.LookupEnv("TELEGRAM_ALARM_TOPIC_ID")
	if !telegramAlarmTopicIdExists {
		panic(errors.New("TELEGRAM_ALARM_TOPIC_ID is missing"))
	}

	telegramAlarmTopicId, err := strconv.ParseInt(telegramAlarmTopicIdCandidate, 10, 64)
	if err != nil {
		panic(err)
	}

	notifier := Notifier{
		telegramUrl:           telegramUrl,
		telegramBotApiKey:     telegramBotApiKey,
		telegramChatId:        telegramChatId,
		telegramReportTopicId: telegramReportTopicId,
		telegramAlarmTopicId:  telegramAlarmTopicId,
	}

	lambda.Start(notifier.Notify)
}
