package main

import (
	"errors"
	"os"
	"strconv"
	"strings"

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

	telegramInsightsTopicIdCandidate, telegramInsightsTopicIdExists := os.LookupEnv("TELEGRAM_INSIGHTS_TOPIC_ID")
	if !telegramInsightsTopicIdExists {
		panic(errors.New("TELEGRAM_INSIGHTS_TOPIC_ID is missing"))
	}

	telegramInsightsTopicId, err := strconv.ParseInt(telegramInsightsTopicIdCandidate, 10, 64)
	if err != nil {
		panic(err)
	}

	telegramInsightsAlarmsRaw, telegramInsightsAlarmsExists := os.LookupEnv("TELEGRAM_INSIGHTS_ALARMS")
	if !telegramInsightsAlarmsExists {
		panic(errors.New("TELEGRAM_INSIGHTS_ALARMS is missing"))
	}

	telegramInsightsAlarms := strings.Split(telegramInsightsAlarmsRaw, ";")

	telegramHealthTopicIdCandidate, telegramHealthTopicIdExists := os.LookupEnv("TELEGRAM_HEALTH_TOPIC_ID")
	if !telegramHealthTopicIdExists {
		panic(errors.New("TELEGRAM_HEALTH_TOPIC_ID is missing"))
	}

	telegramHealthTopicId, err := strconv.ParseInt(telegramHealthTopicIdCandidate, 10, 64)
	if err != nil {
		panic(err)
	}

	telegramHealthAlarmsRaw, telegramHealthAlarmsExists := os.LookupEnv("TELEGRAM_HEALTH_ALARMS")
	if !telegramHealthAlarmsExists {
		panic(errors.New("TELEGRAM_HEALTH_ALARMS is missing"))
	}

	telegramHealthAlarms := strings.Split(telegramHealthAlarmsRaw, ";")

	notifier := Notifier{
		telegramUrl:             telegramUrl,
		telegramBotApiKey:       telegramBotApiKey,
		telegramChatId:          telegramChatId,
		telegramInsightsTopicId: telegramInsightsTopicId,
		telegramInsightsAlarms:  telegramInsightsAlarms,
		telegramHealthTopicId:   telegramHealthTopicId,
		telegramHealthAlarms:    telegramHealthAlarms,
	}

	lambda.Start(notifier.Notify)
}
